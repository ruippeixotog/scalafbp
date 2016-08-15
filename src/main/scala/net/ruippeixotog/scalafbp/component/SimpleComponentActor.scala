package net.ruippeixotog.scalafbp.component

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorRef }
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{ HandledCompletely, Inner }

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

  trait VarDefinition extends Actor with ReceivePipeline {
    def component: Component

    private[this] val sourcesMap =
      component.inPorts.map(_.id -> Var.undefined[Any]()).toMap

    implicit class VarEnabledInPort[A](inPort: InPort[A]) {
      def value: Var[A] = sourcesMap(inPort.id).asInstanceOf[Var[A]]
    }

    implicit class RichVar[A](v: Var[A]) {
      def pipeTo(outPort: OutPort[A]): outPort.type = {
        v.foreach(sender() ! Outgoing(outPort.id, _))
        outPort
      }
    }

    pipelineInner {
      case msg @ Incoming(port, data) =>
        sourcesMap(port).set(data)
        Inner(msg)
    }

    def receive: Receive = Actor.emptyBehavior
  }

  trait PortFreezing extends Actor with ReceivePipeline {
    private[this] var frozenPorts = Map[String, Option[Queue[(Any, ActorRef)]]]()

    implicit class FreezableInPort[A](inPort: InPort[A]) {
      def isFrozen = frozenPorts.get(inPort.id).exists(_.isDefined)

      def freeze() = frozenPorts += (inPort.id -> Some(Queue.empty))

      def unfreeze() = frozenPorts.get(inPort.id).flatten.foreach { queue =>
        queue.foreach((self.tell _).tupled)
        frozenPorts -= inPort.id
      }

      def isIgnored = frozenPorts.get(inPort.id).contains(None)

      def ignore() = frozenPorts += (inPort.id -> None)

      def unignore() = frozenPorts.get(inPort.id).foreach {
        case None => frozenPorts -= inPort.id
        case _ => // nothing to do here
      }
    }

    private[this] def handleMessage(msg: Any, port: String) = frozenPorts.get(port) match {
      case None => Inner(msg)
      case Some(None) => HandledCompletely
      case Some(Some(queue)) =>
        frozenPorts += (port -> Some(queue.enqueue(msg, sender())))
        HandledCompletely
    }

    pipelineOuter {
      case msg @ Incoming(port, _) => handleMessage(msg, port)
      case msg @ InPortDisconnected(port) => handleMessage(msg, port)
    }
  }
}
