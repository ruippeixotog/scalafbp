package net.ruippeixotog.scalafbp.component

import scala.concurrent.duration.{ FiniteDuration, _ }

import akka.actor.ActorSystem
import akka.testkit.{ TestKit, TestProbe }
import org.specs2.execute.AsResult
import org.specs2.matcher.Matcher
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope

import net.ruippeixotog.scalafbp.component.ComponentActor._

abstract class ComponentSpec extends TestKit(ActorSystem()) with SpecificationLike {
  def component: Component

  implicit class RichInPortList[A](inPorts: List[InPort[A]]) {
    def apply(id: String) = inPorts.find(_.id == id).get
  }

  implicit class RichOutPortList[A](outPorts: List[OutPort[A]]) {
    def apply(id: String) = outPorts.find(_.id == id).get
  }

  trait ComponentInstance extends Scope {
    val componentActor = system.actorOf(component.instanceProps)

    val brokerProbe = TestProbe()
    brokerProbe.watch(componentActor)

    implicit class RichInPort[A](inPort: InPort[A]) {
      def send(data: A): Unit = brokerProbe.send(componentActor, Incoming(inPort.id, data))
      def close(): Unit = brokerProbe.send(componentActor, InPortDisconnected(inPort.id))
    }

    def receive[A](data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsg(Outgoing(outPort.id, data)) must not(throwAn[Exception])
    }

    def receive[A](max: FiniteDuration, data: A): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsg(max, Outgoing(outPort.id, data)) must not(throwAn[Exception])
    }

    def receiveLike[A, R: AsResult](f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsgPF() {
        case Outgoing(outPort.id, data: A @unchecked) if f.isDefinedAt(data) => f(data)
      } must not(throwAn[Exception])
    }

    def receiveLike[A, R: AsResult](max: FiniteDuration)(f: PartialFunction[A, R]): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsgPF(max) {
        case Outgoing(outPort.id, data: A @unchecked) if f.isDefinedAt(data) => f(data)
      } must not(throwAn[Exception])
    }

    def beClosed[A]: Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsg(DisconnectOutPort(outPort.id)) must not(throwAn[Exception])
    }

    def beClosed[A](max: FiniteDuration): Matcher[OutPort[A]] = { outPort: OutPort[A] =>
      brokerProbe.expectMsg(max, DisconnectOutPort(outPort.id)) must not(throwAn[Exception])
    }

    def terminate(max: FiniteDuration = 1.seconds): Matcher[ComponentInstance] = { instance: ComponentInstance =>
      instance.brokerProbe.expectTerminated(instance.componentActor) must not(throwAn[Exception])
    }
  }
}
