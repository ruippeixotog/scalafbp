package net.ruippeixotog.scalafbp.component

import scala.concurrent.duration._
import scala.reflect.ClassTag

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.specs2.execute.AsResult
import org.specs2.matcher.Matcher
import org.specs2.specification.Scope

import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.ComponentSpec.TestBrokerSupervisorStrategy

abstract class ComponentSpec extends AkkaSpecification {
  def component: Component

  implicit class RichInPortList(inPorts: List[InPort[_]]) {
    def apply[A](id: String) = inPorts.find(_.id == id).get.asInstanceOf[InPort[A]]
  }

  implicit class RichOutPortList(outPorts: List[OutPort[_]]) {
    def apply[A](id: String) = outPorts.find(_.id == id).get.asInstanceOf[OutPort[A]]
  }

  trait ComponentInstance extends Scope {
    def component = ComponentSpec.this.component

    private[this] val outPortProbes = component.outPorts.map(_.id -> TestProbe()).toMap
    private[this] val outputProbe = TestProbe()
    private[this] val errorProbe = TestProbe()

    private[this] val brokerActor = TestActorRef(new Actor {
      override val supervisorStrategy = new TestBrokerSupervisorStrategy(errorProbe)

      def receive = {
        case msg @ Outgoing(port, data) => outPortProbes(port).ref.forward(msg)
        case msg @ DisconnectOutPort(port) => outPortProbes(port).ref.forward(msg)
        case msg: ComponentActor.ClientCommand => outputProbe.ref.forward(msg)
      }
    })

    private[this] val componentActor = brokerActor.underlyingActor.context.actorOf(component.instanceProps)
    private[this] val componentWatcherProbe = TestProbe()
    componentWatcherProbe.watch(componentActor)

    implicit class RichInPort[A](inPort: InPort[A]) {
      def send(data: A): Unit = componentActor ! Incoming(inPort.id, data)
      def close(): Unit = componentActor ! InPortDisconnected(inPort.id)
    }

    implicit class RichOutPort[A](outPort: OutPort[A]) {
      def close(): Unit = componentActor ! OutPortDisconnected(outPort.id)
    }

    def emit[A](data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receive(Outgoing(outPort.id, data))
    }

    def emit[A](max: FiniteDuration, data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receiveWithin(max)(Outgoing(outPort.id, data))
    }

    def emitAllOf[A](data: A*): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receive.allOf(data.map(Outgoing(outPort.id, _)): _*)
    }

    def emitAllOf[A](max: FiniteDuration, data1: A, data: A*): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receiveWithin(max).allOf(data.map(Outgoing(outPort.id, _)): _*)
    }

    def emitLike[A: ClassTag, R: AsResult](f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receive.like {
        case Outgoing(outPort.id, data: A) if f.isDefinedAt(data) => f(data)
      }
    }

    def emitLike[A: ClassTag, R: AsResult](max: FiniteDuration)(f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receiveWithin(max).like {
        case Outgoing(outPort.id, data: A) if f.isDefinedAt(data) => f(data)
      }
    }

    def emitWhich[A: ClassTag, R: AsResult](f: A => R) = emitLike(PartialFunction(f))
    def emitWhich[A: ClassTag, R: AsResult](max: FiniteDuration)(f: A => R) = emitLike(max)(PartialFunction(f))

    def emitNothing[A]: Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must not(receiveMessage)
    }

    def emitNothing[A](max: FiniteDuration): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must not(receiveMessageWithin(max))
    }

    def beClosed[A]: Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receive(DisconnectOutPort(outPort.id))
    }

    def beClosed[A](max: FiniteDuration): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id) must receiveWithin(max)(DisconnectOutPort(outPort.id))
    }

    def sendOutput(msg: String): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      outputProbe must receive(ComponentActor.Message(msg))
    }

    def sendChangeIcon(icon: String): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      outputProbe must receive(ComponentActor.ChangeIcon(icon))
    }

    def terminate(max: FiniteDuration = 1.seconds): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      componentWatcherProbe.expectTerminated(componentActor) must not(throwAn[Exception])
    }

    def terminateWithError(max: FiniteDuration = 1.seconds): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      componentWatcherProbe.expectTerminated(componentActor) must not(throwAn[Exception])
      errorProbe must receive[Throwable]
    }
  }
}

object ComponentSpec {
  // TODO find a way not to replicate this here (original is in `scalafbp-runtime`)
  def stoppingDecider: Decider = {
    case _: Exception => Stop
  }

  // TODO find a way not to replicate this here (original is in `scalafbp-runtime`)
  class TestBrokerSupervisorStrategy(errorProbe: TestProbe) extends OneForOneStrategy()(stoppingDecider) {

    override def handleFailure(
      context: ActorContext,
      child: ActorRef,
      cause: Throwable,
      stats: ChildRestartStats,
      children: Iterable[ChildRestartStats]): Boolean = {
      cause match {
        case _: ActorKilledException | _: DeathPactException =>
        case ex: Exception => errorProbe.ref ! cause
      }
      super.handleFailure(context, child, cause, stats, children)
    }

    override val loggingEnabled = false
  }
}
