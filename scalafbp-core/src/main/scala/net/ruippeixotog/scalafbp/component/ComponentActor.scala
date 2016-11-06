package net.ruippeixotog.scalafbp.component

import akka.actor.Actor
import rx.lang.scala.JavaConverters._
import rx.lang.scala.Observable
import rx.subjects.UnicastSubject

import net.ruippeixotog.scalafbp.component.ComponentActor._

abstract class ComponentActor[C <: Component](val component: C) extends Actor {

  private[this] var openInPorts = component.inPorts.map(_.id).toSet
  private[this] var openOutPorts = component.outPorts.map(_.id).toSet
  private[this] val deadLetters = context.system.deadLetters
  private[this] val subjects = component.inPorts.map(_.id -> UnicastSubject.create[Any]()).toMap

  final def broker = Option(context).fold(deadLetters)(_.parent)

  val terminationPolicy: Seq[TerminationRule] =
    if (component.inPorts.isEmpty) List(OnAllOutputPortsClosed)
    else if (component.outPorts.isEmpty) List(OnAllInputPortsClosed)
    else List(OnAllInputPortsClosed, OnAllOutputPortsClosed)

  implicit final class RxEnabledInPort[A](inPort: InPort[A]) {
    def stream = subjects(inPort.id).asObservable.asScala.asInstanceOf[Observable[A]]
  }

  implicit final class PipeableObservable[A](obs: Observable[A]) {
    def pipeTo(outPort: OutPort[A]): Unit = obs.foreach(broker ! Outgoing(outPort.id, _))
  }

  private[this] def shouldTerminate() = {
    openInPorts.isEmpty && terminationPolicy.contains(OnAllInputPortsClosed) ||
      openOutPorts.isEmpty && terminationPolicy.contains(OnAllOutputPortsClosed) ||
      openInPorts.isEmpty && openOutPorts.isEmpty && terminationPolicy.contains(OnAllPortsClosed)
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
  sealed trait ComponentMessage

  case class Incoming(port: String, data: Any) extends ComponentMessage
  case class Outgoing(port: String, data: Any) extends ComponentMessage

  case class DisconnectInPort(port: String) extends ComponentMessage
  case class DisconnectOutPort(port: String) extends ComponentMessage
  case class InPortDisconnected(port: String) extends ComponentMessage
  case class OutPortDisconnected(port: String) extends ComponentMessage

  sealed trait ClientCommand
  sealed trait Output extends ClientCommand
  case class Message(message: String) extends Output
  case class PreviewURL(message: String, url: String) extends Output
  case class ChangeIcon(newIcon: String) extends ClientCommand

  sealed trait TerminationRule
  case object OnAllInputPortsClosed extends TerminationRule
  case object OnAllOutputPortsClosed extends TerminationRule
  case object OnAllPortsClosed extends TerminationRule
}
