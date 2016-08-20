package net.ruippeixotog.scalafbp.component

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorRef }
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{ HandledCompletely, Inner }
import rx.lang.scala.{ Observable, Subject }

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor._
import net.ruippeixotog.scalafbp.util.Var

abstract class SimpleComponentActor[C <: Component](val component: C) extends Actor with AutoTerminate

object SimpleComponentActor {

  trait AutoTerminate extends ReceivePipeline { this: Actor =>
    def component: Component

    private[this] var openInPorts = component.inPorts.map(_.id).toSet

    pipelineInner {
      case msg @ InPortDisconnected(port) =>
        openInPorts -= port
        if (openInPorts.isEmpty) context.stop(self)
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

  trait RxDefinition extends Actor with ReceivePipeline {
    def component: Component

    private[this] val subjectsMap =
      component.inPorts.map(_.id -> Subject[Any]()).toMap

    implicit class RxEnabledInPort[A](val inPort: InPort[A]) {
      def stream: Observable[A] = subjectsMap(inPort.id).asInstanceOf[Subject[A]]

      def bufferedStream: Observable[A] = {
        val str = stream.replay
        str.connect
        str
      }
    }

    implicit class RichObservable[A](val obs: Observable[A]) {
      def pipeTo(outPort: OutPort[A]): obs.type = {
        obs.foreach(sender() ! Outgoing(outPort.id, _))
        obs
      }
    }

    pipelineInner {
      case msg @ Incoming(port, data) =>
        subjectsMap(port).onNext(data)
        Inner(msg)

      case msg @ InPortDisconnected(port) =>
        subjectsMap(port).onCompleted()
        Inner(msg)
    }

    def receive: Receive = Actor.emptyBehavior
  }

  trait PortFlowControl extends Actor with ReceivePipeline {

    private[this] sealed trait Action
    private[this] case object Ignore extends Action
    private[this] case object IgnoreIncoming extends Action
    private[this] case class Freeze(stash: Queue[(Any, ActorRef)]) extends Action
    private[this] case object RequireFirst extends Action

    private[this] var portState = Map[String, Action]()

    implicit class FreezableInPort[A](inPort: InPort[A]) {
      def isFrozen = portState.get(inPort.id).exists(_.isInstanceOf[Freeze])
      def freeze() = portState += (inPort.id -> Freeze(Queue.empty))

      def unfreeze() = portState.get(inPort.id) match {
        case Some(Freeze(stash)) =>
          stash.foreach((self.tell _).tupled)
          portState -= inPort.id

        case _ => // do nothing
      }

      def isIgnored = portState.get(inPort.id).contains(Ignore)
      def ignore() = portState += (inPort.id -> Ignore)

      def unignore() = portState.get(inPort.id) match {
        case Some(Ignore) => portState -= inPort.id
        case _ => // do nothing
      }

      def isFirstRequired = portState.get(inPort.id).contains(RequireFirst)
      def requireFirst() = portState += (inPort.id -> RequireFirst)
    }

    private[this] def handleMessage(msg: Any, port: String) = portState.get(port) match {
      case None => Inner(msg)
      case Some(Ignore) => HandledCompletely
      case Some(IgnoreIncoming) if msg.isInstanceOf[Incoming] => HandledCompletely
      case Some(IgnoreIncoming) => Inner(msg)

      case Some(Freeze(stash)) =>
        portState += (port -> Freeze(stash.enqueue(msg, sender())))
        HandledCompletely

      case Some(RequireFirst) if msg.isInstanceOf[Incoming] =>
        portState += (port -> IgnoreIncoming)
        sender() ! DisconnectInPort(port)
        Inner(msg)

      case Some(RequireFirst) =>
        context.stop(self) // port was disconnected without receiving any Incoming message
        HandledCompletely
    }

    pipelineOuter {
      case msg @ Incoming(port, _) => handleMessage(msg, port)
      case msg @ InPortDisconnected(port) => handleMessage(msg, port)
    }
  }
}
