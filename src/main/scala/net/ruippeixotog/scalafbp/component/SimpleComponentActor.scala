package net.ruippeixotog.scalafbp.component

import akka.actor.Actor

import net.ruippeixotog.scalafbp.component.ComponentActor.InPortDisconnected

abstract class SimpleComponentActor(component: Component) extends Actor {
  private[this] var openInPorts = component.inPorts.length

  final def receive = ({
    case InPortDisconnected(port) =>
      openInPorts -= 1
      if (openInPorts == 0) context.stop(self)

  }: Actor.Receive).orElse(receiveData)

  def receiveData: Actor.Receive
}
