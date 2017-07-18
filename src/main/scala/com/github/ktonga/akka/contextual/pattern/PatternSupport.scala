package com.github.ktonga.akka.contextual.pattern

import com.github.ktonga.akka.contextual.actor.MessageContext._
import com.github.ktonga.akka.contextual.actor.Implicits

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask => akkaAsk}

import scala.reflect.ClassTag


trait AskSupport {
  implicit def ask(actorRef: ActorRef): ContextualAskableActorRef =
    new ContextualAskableActorRef(actorRef)
}

final class ContextualAskableActorRef(val actorRef: ActorRef) extends AnyVal {
  private def ?+(msg: Msg[_])(implicit timeout: Timeout): Future[Msg[_]] = {
   akkaAsk(actorRef, msg).mapTo[Msg[_]]
  }
  def wrappedAsk(msg: Msg[_])(implicit timeout: Timeout): Future[Msg[_]] = {
    ?+(msg)
  }

  def askWithCtx(msg: Msg[_])(implicit timeout: Timeout): Future[Msg[_]] = {
    akkaAsk(actorRef, msg).mapTo[Msg[_]]
  }
}

trait PipeToSupport{

  implicit def pipe[T](future: Future[T])(implicit executionContext: ExecutionContext):
   ContextualPimpedFuture[T] = new ContextualPimpedFuture(future)

}

final class ContextualPimpedFuture[T](val future: Future[T])(implicit executionContext: ExecutionContext) extends Implicits {


  def |+(recipient: ActorRef)(implicit ctx: Option[MsgCtx], sender: ActorRef = Actor.noSender): Future[T] = pipeWithContext(recipient)
  def pipeWithContext(recipient: ActorRef)(implicit ctx: Option[MsgCtx], sender: ActorRef = Actor.noSender):
  Future[T] = {
    future onComplete {
      case Success(Msg(r, _)) ⇒ recipient !+ r
      case Success(r) ⇒ recipient !+ r
      case Failure(f) ⇒ recipient !+ Status.Failure(f)
    }
    future
  }

  def onCompleted[U,S](f: Try[T] => U,mapTo:S): Unit = {
    future.onComplete(f)
  }
  def mapResponseTo[S](implicit tag: ClassTag[S]): Future[S]  = {
    (future.map{ f=>
      if (f.isInstanceOf[Msg[_]]){
        f.asInstanceOf[Msg[_]].msg
      }else{
        future.mapTo[S]
      }
    }).mapTo[S]
  }







}

final class ContextualEnrichedFuture[T](val future: Future[Msg[T]])(implicit executionContext: ExecutionContext) extends Implicits {
  def |+(recipient: ActorRef)(implicit ctx: Option[MsgCtx], sender: ActorRef = Actor.noSender): Future[Msg[T]] =
    pipeWithContext(recipient)

  def pipeWithContext(recipient: ActorRef)(implicit ctx: Option[MsgCtx], sender: ActorRef = Actor.noSender):
  Future[Msg[T]] = {
    future onComplete {
      case Success(Msg(r, _)) ⇒ recipient !+ r
      case Success(r) ⇒ recipient !+ r
      case Failure(f) ⇒ recipient !+ Status.Failure(f)
    }
    future
  }


}


