package com.github.ktonga.akka.contextual.pattern

import com.github.ktonga.akka.contextual.actor.MessageContext._
import com.github.ktonga.akka.contextual.actor.Implicits

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask => akkaAsk}


trait AskSupport {
  implicit def ask(actorRef: ActorRef): ContextualAskableActorRef = new ContextualAskableActorRef(actorRef)
}

final class ContextualAskableActorRef(val actorRef: ActorRef) extends AnyVal {
  def ?+(msg: Msg[_])(implicit timeout: Timeout): Future[Msg[_]] = {
    val answer = akkaAsk(actorRef, msg).mapTo[Msg[_]]

    answer

  }

  def askWithCtx(msg: Msg[_])(implicit timeout: Timeout): Future[Msg[_]] = {
    akkaAsk(actorRef, msg).mapTo[Msg[_]]
  }
}

trait PipeToSupport {
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
  def onCompletion[U](f: (Try[Msg[T]]) => U): Future[Msg[T]] = {
    println(s"onCompletion called with $f")

    val p = Promise[Msg[T]]()
    //f(future)

  //  future.onComplete { r: Try[Msg[T]] =>  f(r); p.complete(r) }

    p.future
  }

}


