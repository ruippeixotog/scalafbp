package net.ruippeixotog.scalafbp.component

import akka.actor.Actor
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor._
import net.ruippeixotog.scalafbp.util.Var

abstract class SimpleComponentActor[C <: Component](val component: C) extends Actor with AutoTerminate

object SimpleComponentActor {

  trait AutoTerminate extends ReceivePipeline { this: Actor =>
    def component: Component

    private[this] var openInPorts = component.inPorts.length

    pipelineInner {
      case msg @ InPortDisconnected(port) =>
        openInPorts -= 1
        if (openInPorts == 0) context.stop(self)
        Inner(msg)
    }
  }

  trait VarDefinition extends Actor {
    def component: Component

    private[this] val sourcesMap =
      component.inPorts.map(_.id -> Var.undefined[Any]()).toMap

    implicit class RichInPort[A](inPort: InPort[A]) {
      def value: Var[A] = sourcesMap(inPort.id).asInstanceOf[Var[A]]
    }

    implicit class RichVar[A](v: Var[A]) {
      def pipeTo(outPort: OutPort[A]): outPort.type = {
        v.foreach(sender() ! Outgoing(outPort.id, _))
        outPort
      }
    }

    final def receive = {
      case Incoming(port, data) => sourcesMap(port).set(data)
    }
  }
}
