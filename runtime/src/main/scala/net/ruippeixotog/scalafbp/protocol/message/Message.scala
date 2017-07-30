package net.ruippeixotog.scalafbp.protocol.message

import fommil.sjs.FamilyFormats
import shapeless._
import spray.json._

sealed trait Message {
  private[message] def wrap: ProtocolWrapper
}

private[protocol] sealed trait RuntimeMessage extends Message {
  private[message] def wrap = ProtocolWrappers.Runtime(this)
}

private[protocol] object RuntimeMessages {

  case class GetRuntime(
    secret: String) extends RuntimeMessage

  case class Runtime(
    `type`: String,
    version: String,
    capabilities: List[String],
    allCapabilities: List[String],
    id: Option[String],
    label: Option[String],
    graph: Option[String]) extends RuntimeMessage

  case class Port(
    id: String,
    `type`: String,
    description: String,
    addressable: Boolean,
    required: Boolean)

  case class Ports(
    graph: String,
    inPorts: List[Port],
    outPorts: List[Port]) extends RuntimeMessage

  case class Packet(
    port: String,
    event: String,
    payload: Option[String],
    graph: String,
    secret: String) extends RuntimeMessage

  case class Error(
    message: String) extends RuntimeMessage
}

private[protocol] sealed trait GraphMessage extends Message {
  private[message] def wrap = ProtocolWrappers.Graph(this)
}

private[protocol] object GraphMessages {

  case class Clear(
    id: String,
    name: Option[String],
    library: Option[String],
    main: Option[Boolean],
    icon: Option[String],
    description: Option[String],
    secret: String) extends GraphMessage

  case class AddNode(
    id: String,
    component: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends GraphMessage

  case class RemoveNode(
    id: String,
    graph: String,
    secret: String) extends GraphMessage

  case class RenameNode(
    from: String,
    to: String,
    graph: String,
    secret: String) extends GraphMessage

  case class ChangeNode(
    id: String,
    metadata: Map[String, JsValue],
    graph: String,
    secret: String) extends GraphMessage

  case class Edge(
    node: String,
    port: String,
    index: Option[Int])

  case class AddEdge(
    src: Edge,
    tgt: Edge,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends GraphMessage

  case class RemoveEdge(
    src: Edge,
    tgt: Edge,
    graph: String,
    secret: String) extends GraphMessage

  case class ChangeEdge(
    src: Edge,
    tgt: Edge,
    metadata: Map[String, JsValue],
    graph: String,
    secret: String) extends GraphMessage

  case class Initial(data: JsValue)

  case class AddInitial(
    src: Initial,
    tgt: Edge,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends GraphMessage

  case class RemoveInitial(
    tgt: Edge,
    graph: String,
    secret: String) extends GraphMessage

  case class AddInPort(
    public: String,
    node: String,
    port: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends GraphMessage

  case class RemoveInPort(
    public: String,
    graph: String,
    secret: String) extends GraphMessage

  case class RenameInPort(
    from: String,
    to: String,
    graph: String,
    secret: String) extends GraphMessage

  case class AddOutPort(
    public: String,
    node: String,
    port: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends GraphMessage

  case class RemoveOutPort(
    public: String,
    graph: String,
    secret: String) extends GraphMessage

  case class RenameOutPort(
    from: String,
    to: String,
    graph: String,
    secret: String) extends GraphMessage

  case class Error(
    message: String) extends GraphMessage
}

private[protocol] sealed trait ComponentMessage extends Message {
  private[message] def wrap = ProtocolWrappers.Component(this)
}

private[protocol] object ComponentMessages {

  case class List(
    secret: String) extends ComponentMessage

  case class InPort(
    id: String,
    `type`: String,
    description: String,
    addressable: Boolean,
    required: Boolean,
    values: Option[scala.List[String]],
    default: Option[String])

  case class OutPort(
    id: String,
    `type`: String,
    description: String,
    addressable: Boolean,
    required: Boolean)

  case class Component(
    name: String,
    description: Option[String],
    icon: Option[String],
    subgraph: Boolean,
    inPorts: scala.List[InPort],
    outPorts: scala.List[OutPort]) extends ComponentMessage

  case class ComponentsReady(
    _value: Int) extends ComponentMessage

  case class Error(
    message: String) extends ComponentMessage
}

private[protocol] sealed trait NetworkMessage extends Message {
  private[message] def wrap = ProtocolWrappers.Network(this)
}

private[protocol] object NetworkMessages {

  case class Start(
    graph: String,
    secret: String) extends NetworkMessage

  case class GetStatus(
    graph: String,
    secret: String) extends NetworkMessage

  case class Stop(
    graph: String,
    secret: String) extends NetworkMessage

  case class Persist(
    secret: String) extends NetworkMessage

  case class Started(
    graph: String,
    time: Long,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long]) extends NetworkMessage

  case class Status(
    graph: String,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long],
    debug: Option[Boolean]) extends NetworkMessage

  case class Stopped(
    graph: String,
    time: Long,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long]) extends NetworkMessage

  case class Debug(
    enable: Boolean,
    graph: String,
    secret: String) extends NetworkMessage

  case class Icon(
    id: String,
    icon: String,
    graph: String) extends NetworkMessage

  case class Output(
    message: String,
    `type`: Option[String],
    url: Option[String]) extends NetworkMessage

  case class Error(
    message: String) extends NetworkMessage

  case class ProcessError(
    id: String,
    error: String,
    graph: String) extends NetworkMessage

  case class Port(node: String, port: String)

  case class Connect(
    id: String,
    src: Option[Port],
    tgt: Port,
    graph: String,
    subgraph: Option[List[String]]) extends NetworkMessage

  case class Data(
    id: String,
    src: Option[Port],
    tgt: Port,
    data: JsValue,
    graph: String,
    subgraph: Option[List[String]]) extends NetworkMessage

  case class Disconnect(
    id: String,
    src: Option[Port],
    tgt: Port,
    graph: String,
    subgraph: Option[List[String]]) extends NetworkMessage

  // TODO
}

private[protocol] sealed trait TraceMessage extends Message {
  private[message] def wrap = ProtocolWrappers.Trace(this)
}

private[protocol] object TraceMessages {

  case class Start(
    graph: String,
    secret: String,
    buffersize: Option[Int]) extends TraceMessage
}

private[message] sealed trait ProtocolWrapper {
  def payload: Message
}

object ProtocolWrappers {
  private[message] case class Runtime(payload: RuntimeMessage) extends ProtocolWrapper
  private[message] case class Graph(payload: GraphMessage) extends ProtocolWrapper
  private[message] case class Component(payload: ComponentMessage) extends ProtocolWrapper
  private[message] case class Network(payload: NetworkMessage) extends ProtocolWrapper
  private[message] case class Trace(payload: TraceMessage) extends ProtocolWrapper
}

object Message {
  private object JsonProtocol extends DefaultJsonProtocol with FamilyFormats with CustomJsonFormatHints {
    implicit val jsValueJsonFormat = JsValueFormat // to help the compiler find the implicit for RootJsonformat[ProtocolWrapper]
    implicit val messageCoproductHint = new MessageCoproductHint("protocol", "command", "payload")

    val wrapperJsonFormat: RootJsonFormat[ProtocolWrapper] = cachedImplicit
  }

  implicit object MessageJsonFormat extends RootJsonFormat[Message] {
    def read(json: JsValue) = JsonProtocol.wrapperJsonFormat.read(json).payload
    def write(obj: Message) = JsonProtocol.wrapperJsonFormat.write(obj.wrap)
  }

  implicit def messageSubtypeJsonWriter[T <: Message] = new RootJsonWriter[T] {
    def write(obj: T) = MessageJsonFormat.write(obj)
  }
}
