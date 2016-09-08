package net.ruippeixotog.scalafbp.component

import scala.concurrent.duration._

import akka.actor._
import akka.testkit.{ TestActorRef, TestKit, TestProbe }
import org.specs2.execute.AsResult
import org.specs2.matcher.Matcher
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.runtime.NetworkBrokerSupervisorStrategy

abstract class ComponentSpec extends TestKit(ActorSystem()) with SpecificationLike {
  def component: Component

  implicit class RichInPortList[A](inPorts: List[InPort[A]]) {
    def apply(id: String) = inPorts.find(_.id == id).get
  }

  implicit class RichOutPortList[A](outPorts: List[OutPort[A]]) {
    def apply(id: String) = outPorts.find(_.id == id).get
  }

  trait ComponentInstance extends Scope {
    private[this] val outPortProbes = component.outPorts.map(_.id -> TestProbe()).toMap
    private[this] val outputProbe = TestProbe()
    private[this] val processErrorProbe = TestProbe()

    private[this] val brokerActor = TestActorRef(new Actor {
      override def supervisorStrategy = new NetworkBrokerSupervisorStrategy({ (_, cause) =>
        processErrorProbe.ref ! cause
      })

      def receive = {
        case msg @ Outgoing(port, data) => outPortProbes(port).ref.forward(msg)
        case msg @ DisconnectOutPort(port) => outPortProbes(port).ref.forward(msg)
        case msg: ComponentActor.Output => outputProbe.ref.forward(msg)
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

    def receive[A](data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsg(Outgoing(outPort.id, data)) must not(throwAn[Exception])
    }

    def receive[A](max: FiniteDuration, data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsg(max, Outgoing(outPort.id, data)) must not(throwAn[Exception])
    }

    def receiveAllOf[A](data: A*): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgAllOf(data.map(Outgoing(outPort.id, _)): _*) must not(throwAn[Exception])
    }

    def receiveAllOf[A](max: FiniteDuration, data: A*): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgAllOf(max, data.map(Outgoing(outPort.id, _)): _*) must not(throwAn[Exception])
    }

    def receiveWhich[A, R: AsResult](f: A => R): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgPF() {
        case Outgoing(outPort.id, data: A @unchecked) => f(data)
      } must not(throwAn[Exception])
    }

    def receiveWhich[A, R: AsResult](max: FiniteDuration)(f: A => R): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgPF(max) {
        case Outgoing(outPort.id, data: A @unchecked) => f(data)
      } must not(throwAn[Exception])
    }

    def receiveLike[A, R: AsResult](f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgPF() {
        case Outgoing(outPort.id, data: A @unchecked) if f.isDefinedAt(data) => f(data)
      } must not(throwAn[Exception])
    }

    def receiveLike[A, R: AsResult](max: FiniteDuration)(f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsgPF(max) {
        case Outgoing(outPort.id, data: A @unchecked) if f.isDefinedAt(data) => f(data)
      } must not(throwAn[Exception])
    }

    def receiveNothing[A]: Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectNoMsg() must not(throwAn[Exception])
    }

    def receiveNothing[A](max: FiniteDuration): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectNoMsg(max) must not(throwAn[Exception])
    }

    def beClosed[A]: Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsg(DisconnectOutPort(outPort.id)) must not(throwAn[Exception])
    }

    def beClosed[A](max: FiniteDuration): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      outPortProbes(outPort.id).expectMsg(max, DisconnectOutPort(outPort.id)) must not(throwAn[Exception])
    }

    def sendOutput(msg: String): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      outputProbe.expectMsg(ComponentActor.Message(msg)) must not(throwAn[Exception])
    }

    def terminate(max: FiniteDuration = 1.seconds): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      componentWatcherProbe.expectTerminated(componentActor) must not(throwAn[Exception])
    }

    def terminateWithProcessError(max: FiniteDuration = 1.seconds): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      componentWatcherProbe.expectTerminated(componentActor) must not(throwAn[Exception])
      processErrorProbe.expectMsgType[Throwable] must not(throwAn[Exception])
    }
  }
}
