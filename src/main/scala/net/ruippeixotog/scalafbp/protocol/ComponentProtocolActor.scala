package net.ruippeixotog.scalafbp.protocol

import akka.actor.Actor

import net.ruippeixotog.scalafbp.component
import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.{ List => ListComponents, _ }
import net.ruippeixotog.scalafbp.protocol.message.{ Component => ComponentProtocol }

class ComponentProtocolActor extends Actor {

  def convertInPort(port: component.InPort[_]) = InPort(
    port.id, port.dataType, port.description, port.addressable, port.required,
    port.values.map(_.map(_.toString)), port.default.map(_.toString))

  def convertOutPort(port: component.OutPort[_]) = OutPort(
    port.id, port.dataType, port.description, port.addressable, port.required)

  def convertComponent(comp: component.Component) = Component(
    comp.name, Some(comp.description), comp.icon, comp.isSubgraph,
    comp.inPorts.map(convertInPort), comp.outPorts.map(convertOutPort))

  def wrap(payload: Payload) = ComponentProtocol(payload)

  def receive = {
    case _: ListComponents =>
      ComponentRegistry.registry.values.foreach { comp => sender() ! wrap(convertComponent(comp)) }
      sender() ! wrap(ComponentsReady(ComponentRegistry.registry.size))

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
