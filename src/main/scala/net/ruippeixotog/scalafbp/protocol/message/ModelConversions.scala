package net.ruippeixotog.scalafbp.protocol.message

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.{ component, graph }
import net.ruippeixotog.scalafbp.graph.{ NetworkBroker, NetworkController, PortRef }
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.Component
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages.Edge
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.runtime.LogicActor

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

  implicit class FinishedConvertible(val st: NetworkController.Finished) extends AnyVal with ToMessageConvertible {
    def toMessage = Stopped(st.graph, st.time, false, true, Some(st.uptime))
  }

  implicit class PortRefConvertible(val portRef: graph.PortRef) extends AnyVal {
    def toMessagePart = Port(portRef.node, portRef.port)

    def toInRef = s"${portRef.node}() ${portRef.port.toUpperCase}"
    def toOutRef = s"${portRef.port.toUpperCase} ${portRef.node}()"
  }

  implicit class ActivityConvertible(val act: NetworkBroker.Activity) extends AnyVal with ToMessageConvertible {
    private[this] def toRef = s"${act.src.fold("DATA")(_.toInRef)} -> ${act.tgt.toOutRef}"

    def toMessage = act match {
      case connect: NetworkBroker.Connect =>
        Connect(toRef, connect.src.map(_.toMessagePart), connect.tgt.toMessagePart, connect.graph, None)

      case data: NetworkBroker.Data =>
        Data(toRef, data.src.map(_.toMessagePart), data.tgt.toMessagePart, data.data, data.graph, None)

      case disconnect: NetworkBroker.Disconnect =>
        Disconnect(toRef, disconnect.src.map(_.toMessagePart), disconnect.tgt.toMessagePart, disconnect.graph, None)
    }
  }

  implicit class DataConvertible(val conn: NetworkBroker.Data) extends AnyVal with ToMessageConvertible {
    def toMessage = Data(
      s"${conn.src.fold("DATA")(_.toInRef)} -> ${conn.tgt.toOutRef}",
      conn.src.map(_.toMessagePart),
      conn.tgt.toMessagePart,
      conn.data,
      conn.graph,
      None)
  }

  implicit class PortConvertible(val conn: NetworkBroker.Disconnect) extends AnyVal with ToMessageConvertible {
    def toMessage = Disconnect(
      s"${conn.src.fold("DATA")(_.toInRef)} -> ${conn.tgt.toOutRef}",
      conn.src.map(_.toMessagePart),
      conn.tgt.toMessagePart,
      conn.graph,
      None)
  }

  implicit class OutputConvertible(val output: ComponentActor.Output) extends AnyVal with ToMessageConvertible {
    def toMessage = output match {
      case ComponentActor.Message(msg) => Output(msg, Some("message"), None)
      case ComponentActor.PreviewURL(msg, url) => Output(msg, Some("previewurl"), Some(url))
    }
  }

  implicit class ErrorConvertible(val error: LogicActor.Error) extends AnyVal with ToMessageConvertible {
    def toMessage = Error(error.msg)
  }
}

object FromMessageConversions {

  implicit class EdgeConvertible(val edge: Edge) extends AnyVal {
    def toPortRef = graph.PortRef(edge.node, edge.port)
  }
}
