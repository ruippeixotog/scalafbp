package net.ruippeixotog.scalafbp.protocol.message

import net.ruippeixotog.scalafbp.component
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.Component
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages.{ Started, Status, Stopped }
import net.ruippeixotog.scalafbp.runtime.LogicActor

object ModelConversions {

  trait ToMessageConvertible extends Any {
    def toMessage: Message
  }

  implicit class InPortConvertible[A](val port: component.InPort[A]) extends AnyVal {
    def toMessagePart = ComponentMessages.InPort(
      port.id, port.dataType, port.description, port.addressable, port.required,
      port.values.map(_.map(_.toString)), port.default.map(_.toString))
  }

  implicit class OutPortConvertible[A](val port: component.OutPort[A]) extends AnyVal {
    def toMessagePart = ComponentMessages.OutPort(
      port.id, port.dataType, port.description, port.addressable, port.required)
  }

  implicit class ComponentConvertible(val comp: component.Component) extends AnyVal with ToMessageConvertible {
    def toMessage = Component(
      comp.name, Some(comp.description), comp.icon, comp.isSubgraph,
      comp.inPorts.map(_.toMessagePart), comp.outPorts.map(_.toMessagePart))
  }

  implicit class StatusConvertible(val st: LogicActor.Status) extends AnyVal with ToMessageConvertible {
    def toMessage = toStatusMessage

    def toStatusMessage = Status(st.graph, st.running, st.started, st.uptime, None)

    def toStartedMessage(time: Long = System.currentTimeMillis()) =
      Started(st.graph, time, st.running, st.started, st.uptime)

    def toStoppedMessage(time: Long = System.currentTimeMillis()) =
      Stopped(st.graph, time, st.running, st.started, st.uptime)
  }
}
