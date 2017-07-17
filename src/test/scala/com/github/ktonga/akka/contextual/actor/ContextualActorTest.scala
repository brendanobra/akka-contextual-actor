package com.github.ktonga.akka.contextual.actor

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import DummyProtocol.{Ask, Tell, _}
import com.github.ktonga.akka.contextual.pattern.{AskSupport, ask, pipe}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest._
import akka.actor._
import akka.util.Timeout
import com.github.ktonga.akka.contextual.actor.MessageContext.MsgCtx
import com.github.ktonga.akka.contextual.actor.TestProxy.AskProxy

import scala.concurrent._
import scala.concurrent.duration._
import com.github.ktonga.akka.contextual.actor._
import com.github.ktonga.akka.contextual.pattern.{ask, pipe}

import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

/**
  * Created by brendan on 7/16/17.
  */


object DummyProtocol {
  trait DummyMsg

  case class Tell(param: String) extends DummyMsg

  case class Ask(param: String) extends DummyMsg

  case class Forward(param: String) extends DummyMsg

  case class Pipe(param: String) extends DummyMsg

  case class AskResponse(response: String) extends DummyMsg

  case class ForwardResponse(response: String) extends DummyMsg

  case object ForwardBehavior  extends DummyMsg

  case object TellBehavior extends DummyMsg

}

class ActorA(val actorB: ActorRef) extends Actor with TracingActor {

  import com.github.ktonga.akka.contextual.pattern.{ask, pipe}

  implicit val _: Timeout = 5.seconds

  def receive: Receive = {
    case Tell(str) => doTell(str)
    case Ask(question) => (actorB ?+ Ask(s"do you know $question?")) |+ sender
    case fwr: ForwardResponse => log.info("Thanks! {}", fwr)
    case ForwardBehavior => context.become(forwardBehavior, false)
  }

  val forwardBehavior: Receive = {
    case fw: Forward => actorB !+ fw
    case fwr: ForwardResponse => log.info("Thanks! {}", fwr)
    case TellBehavior => context.unbecome()
  }


  def doTell(str: String) = {
    log.info("A received: {}, responding to {}", str,sender())
    sender !+ str.length
    actorB !+ str.length
  }

  override def unhandled(message: Any): Unit = log.error("Unhandled But Controlled Message: {}", message)
}

class ActorB(val actorC: ActorRef) extends Actor with TracingActor {

  import MessageContext._
  import scala.concurrent.ExecutionContext.Implicits.global

  def receive: Receive = {
    case int: Int => useInt(int)
    case Ask(question) => {
      val resp: Msg[AskResponse] = AskResponse(s"I don't know $question")
      val theSender = sender
      context.system.scheduler.scheduleOnce(2 seconds) {
        theSender !+ resp
      }
    }
    case fw: Forward => actorC >+ fw
  }

  def useInt(int: Int) = {
    log.info("B received length: {}", int)
  }
}

class ActorC extends Actor with TracingActor {

  def receive: Receive = {
    case Forward(str) => sender !+ ForwardResponse(s"They forwarded me this: $str")
  }

}

class TestProxy(testingFor:ActorRef,underTest:ActorRef) extends  TracingActor with AskSupport {
  import com.github.ktonga.akka.contextual.pattern.ask
  import Implicits._
  import scala.util.Random
  import MessageContext._
  import akka.pattern.{ask => dontAsk}
/*
  implicit def mapTry(m:Try[Msg[_]]):Try[Any] = {
    println(s"maptry called with $m")
    Try(m.get.msg)
  }
  */

  override def receive: Receive = {

    case askit:AskProxy[_] =>
      implicit val _: Timeout = 5.seconds
      val w = (underTest ?+ askit.msg)
      println(s"asked got=$w")
      w.onComplete{
        case Success(s) =>
          println(s"got success=$s")
          testingFor ! s.msg
        case Failure(f) =>
          testingFor ! f
      }
/*
      (underTest  ?+ askit.msg) onComplete{
        case Success(s) =>
          testingFor ! s.msg
        case Failure(f) =>
          testingFor ! f
      }
*/
    case msg =>
      msg match {
        case Ask(msg) =>
        case msg =>
          if (sender() == testingFor) {
            log.info(s"forwarding $msg to $underTest")
            underTest ! msg
          }
          else {
            log.info(s"forwarding $msg to $testingFor on behalf of $sender()")
            testingFor !msg
          }

      }


  }
}
object TestProxy {
  def props(testingFor:ActorRef,underTest:ActorRef) = {
    Props(new TestProxy(testingFor,underTest))
  }
  case class TellProxy(msg:Any)
  case class AskProxy[T](msg:Any,result:T)
}

class ContextualActorTest extends TestKit(ActorSystem("tester"))
  with ImplicitSender
  with WordSpecLike with BeforeAndAfterAll {
  val actorC = system.actorOf(Props[ActorC], "actor-c")
  val actorB = system.actorOf(Props(new ActorB(actorC)), "actor-b")
  val actorA = system.actorOf(Props(new ActorA(actorB)), "actor-a")
  val aProxy = system.actorOf(TestProxy.props(self,actorA))
  import com.github.ktonga.akka.contextual.pattern.{ask, pipe}
  import Implicits._
  import scala.util.Random
  import MessageContext._
  "A ContextActor" should {
    "Respond with an unwrappedMessage" in {
      implicit val _: Timeout = 5.seconds

      implicit val ctx = Some(MsgCtx(Map("requestId" -> s"implicit-${Random.nextInt(1000)}")))

      aProxy !+ Tell("With Implicit Context")

      expectMsgPF(){
        case i:Int =>
          assert( i == 21)

      }

      aProxy ! AskProxy(Ask("With Implicit Context"),self)

      expectMsgPF(){
        case a:AskResponse =>
        case msg =>
          assert(false, s"should not get $msg")
      }

    }
  }
}
