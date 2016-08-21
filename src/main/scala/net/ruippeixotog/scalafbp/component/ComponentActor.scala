package net.ruippeixotog.scalafbp.component

import akka.actor.Actor
import rx.lang.scala.{ Observable, Subject }

import net.ruippeixotog.scalafbp.component.ComponentActor._

abstract class ComponentActor[C <: Component](val component: C) extends Actor {

  private[this] var openInPorts = component.inPorts.map(_.id).toSet
  private[this] var openOutPorts = component.outPorts.map(_.id).toSet
  private[this] val deadLetters = context.system.deadLetters
  private[this] val subjects = component.inPorts.map(_.id -> Subject[Any]()).toMap

  final def broker = Option(context).fold(deadLetters)(_.parent)

  val terminationPolicy: Seq[TerminationRule] =
    if (component.inPorts.isEmpty) List(OnAllOutputPortsClosed)
    else if (component.outPorts.isEmpty) List(OnAllInputPortsClosed)
    else List(OnAllInputPortsClosed, OnAllOutputPortsClosed)

  implicit final class RxEnabledInPort[A](inPort: InPort[A]) {
    def stream: Observable[A] = subjects(inPort.id).asInstanceOf[Subject[A]]

    def bufferedStream: Observable[A] = {
      // TODO this will cache *all* messages *forever*, not just until the first subscriber connects
      val str = stream.replay
      str.connect
      str
    }
  }

  implicit final class PipeableObservable[A](obs: Observable[A]) {
    def pipeTo(outPort: OutPort[A]): Unit = obs.foreach(broker ! Outgoing(outPort.id, _))
  }

  private[this] def shouldTerminate() = {
    if (openInPorts.isEmpty) {
      terminationPolicy.contains(OnAllInputPortsClosed) ||
        (openOutPorts.isEmpty && terminationPolicy.contains(OnAllPortsClosed))
    } else {
      openOutPorts.isEmpty && terminationPolicy.contains(OnAllOutputPortsClosed)
    }
  }

  final def receive = {
    case msg @ Incoming(port, data) =>
      subjects(port).onNext(data)

    case msg @ InPortDisconnected(port) =>
      subjects(port).onCompleted()
      openInPorts -= port
      if (shouldTerminate()) context.stop(self)

    case msg @ OutPortDisconnected(port) =>
      openOutPorts -= port
      if (shouldTerminate()) context.stop(self)
  }
}

object ComponentActor {
  case class Incoming(port: String, data: Any)
  case class Outgoing(port: String, data: Any)

  case class DisconnectInPort(port: String)
  case class DisconnectOutPort(port: String)
  case class InPortDisconnected(port: String)
  case class OutPortDisconnected(port: String) // not used for now

  sealed trait Output
  case class Message(message: String) extends Output
  case class PreviewURL(message: String, url: String) extends Output

  sealed trait TerminationRule
  case object OnAllInputPortsClosed extends TerminationRule
  case object OnAllOutputPortsClosed extends TerminationRule
  case object OnAllPortsClosed extends TerminationRule
}
