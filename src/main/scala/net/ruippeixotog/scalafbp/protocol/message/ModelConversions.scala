package net.ruippeixotog.scalafbp.protocol.message

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.{ component, graph }
import net.ruippeixotog.scalafbp.graph.{ NetworkController, NetworkController$ }
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.Component
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages.Edge
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages.{ Output, Started, Status, Stopped }

object ToMessageConversions {

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

  implicit class StatusConvertible(val st: NetworkController.Status) extends AnyVal with ToMessageConvertible {
    def toMessage = toStatusMessage

    def toStatusMessage = Status(st.graph, st.running, st.started, st.uptime, None)

    def toStartedMessage(time: Long = System.currentTimeMillis()) =
      Started(st.graph, time, st.running, st.started, st.uptime)

    def toStoppedMessage(time: Long = System.currentTimeMillis()) =
      Stopped(st.graph, time, st.running, st.started, st.uptime)
  }

  implicit class OutputConvertible(val output: ComponentActor.Output) extends AnyVal with ToMessageConvertible {
    def toMessage = output match {
      case ComponentActor.Message(msg) => Output(msg, Some("message"), None)
      case ComponentActor.PreviewURL(msg, url) => Output(msg, Some("previewurl"), Some(url))
    }
  }
}

object FromMessageConversions {

  implicit class EdgeConvertible(val edge: Edge) extends AnyVal {
    def toPortRef = graph.PortRef(edge.node, edge.port)
  }
}
