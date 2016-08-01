package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.component
import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.Component.{ List => ListComponents, _ }

class ComponentProtocolActor extends AbstractProtocolActor[ComponentMessage] {

  def convertInPort(port: component.InPort[_]) = InPort(
    port.id, port.dataType, port.description, port.addressable, port.required,
    port.values.map(_.map(_.toString)), port.default.map(_.toString))

  def convertOutPort(port: component.OutPort[_]) = OutPort(
    port.id, port.dataType, port.description, port.addressable, port.required)

  def convertComponent(comp: component.Component) = Component(
    comp.name, Some(comp.description), comp.icon, comp.isSubgraph,
    comp.inPorts.map(convertInPort), comp.outPorts.map(convertOutPort))

  def receiveMessage = {
    case _: ListComponents =>
      ComponentRegistry.registry.values.foreach { comp => sender() ! convertComponent(comp) }
      sender() ! ComponentsReady(ComponentRegistry.registry.size)
  }
}
