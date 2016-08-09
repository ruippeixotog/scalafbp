package net.ruippeixotog.scalafbp.component

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor._
import net.ruippeixotog.scalafbp.util.Var

abstract class SimpleComponentActor(val component: Component) extends Actor with AutoTerminate

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

    def mapInputs(in: List[Var[Any]]): List[Var[Any]]

    private[this] lazy val sourcesMap = {
      val sources = component.inPorts.map(_.id -> Var.undefined[Any]())
      val sinks = mapInputs(sources.map(_._2))

      component.outPorts.zip(sinks).foreach {
        case (port, outVar) => outVar.foreach(sender() ! Outgoing(port.id, _))
      }
      sources.toMap
    }

    final def receive = {
      case Incoming(port, data) => sourcesMap(port).set(data)
    }
  }
}
