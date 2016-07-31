package net.ruippeixotog.scalafbp.protocol.message

import net.ruippeixotog.scalafbp.protocol.message

import fommil.sjs.FamilyFormats
import shapeless._
import spray.json._

sealed trait Payload {
  def toMessage: Message
}

sealed trait Message {
  def payload: Payload
}

private[protocol] case class Runtime(payload: RuntimeMessages.Payload) extends Message
private[protocol] case class Graph(payload: GraphMessages.Payload) extends Message
private[protocol] case class Component(payload: ComponentMessages.Payload) extends Message
private[protocol] case class Network(payload: NetworkMessages.Payload) extends Message
private[protocol] case class Trace(payload: TraceMessages.Payload) extends Message

private[protocol] object RuntimeMessages {
  sealed trait Payload extends message.Payload {
    def toMessage = message.Runtime(this)
  }

  case class GetRuntime(
    secret: String) extends Payload

  case class Runtime(
    `type`: String,
    version: String,
    capabilities: List[String],
    allCapabilities: List[String],
    id: Option[String],
    label: Option[String],
    graph: Option[String]) extends Payload

  case class Port(
    id: String,
    `type`: String,
    description: String,
    addressable: Boolean,
    required: Boolean)

  case class Ports(
    graph: String,
    inPorts: List[Port],
    outPorts: List[Port]) extends Payload

  case class Packet(
    port: String,
    event: String,
    payload: Option[String],
    graph: String,
    secret: String) extends Payload
}

private[protocol] object GraphMessages {
  sealed trait Payload extends message.Payload {
    def toMessage = message.Graph(this)
  }

  case class Clear(
    id: String,
    name: Option[String],
    library: Option[String],
    main: Option[String],
    icon: Option[String],
    description: Option[String],
    secret: String) extends Payload

  case class AddNode(
    id: String,
    component: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class RemoveNode(
    id: String,
    graph: String,
    secret: String) extends Payload

  case class ChangeNode(
    id: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class Edge(
    node: String,
    port: String,
    index: Option[Int])

  case class AddEdge(
    src: Edge,
    tgt: Edge,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class RemoveEdge(
    src: Edge,
    tgt: Edge,
    graph: String,
    secret: String) extends Payload

  case class ChangeEdge(
    src: Edge,
    tgt: Edge,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class Initial(data: JsValue)

  case class AddInitial(
    src: Initial,
    tgt: Edge,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class RemoveInitial(
    tgt: Edge,
    graph: String,
    secret: String) extends Payload

  case class AddInPort(
    public: String,
    node: String,
    port: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload

  case class AddOutPort(
    public: String,
    node: String,
    port: String,
    metadata: Option[Map[String, JsValue]],
    graph: String,
    secret: String) extends Payload
}

private[protocol] object ComponentMessages {
  sealed trait Payload extends message.Payload {
    def toMessage = message.Component(this)
  }

  case class List(
    secret: String) extends Payload

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
    outPorts: scala.List[OutPort]) extends Payload

  case class ComponentsReady(
    _value: Int) extends Payload
}

private[protocol] object NetworkMessages {
  sealed trait Payload extends message.Payload {
    def toMessage = message.Network(this)
  }

  case class Start(
    graph: String,
    secret: String) extends Payload

  case class GetStatus(
    graph: String,
    secret: String) extends Payload

  case class Stop(
    graph: String,
    secret: String) extends Payload

  case class Persist(
    secret: String) extends Payload

  case class Started(
    graph: String,
    time: Long,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long]) extends Payload

  case class Status(
    graph: String,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long],
    debug: Option[Boolean]) extends Payload

  case class Stopped(
    graph: String,
    time: Long,
    running: Boolean,
    started: Boolean,
    uptime: Option[Long]) extends Payload

  case class Debug(
    enable: Boolean,
    graph: String,
    secret: String) extends Payload

  case class Icon(
    id: String,
    icon: String,
    graph: String) extends Payload

  case class Output(
    message: String,
    `type`: Option[String],
    url: Option[String]) extends Payload

  case class Error(
    message: String) extends Payload

  case class ProcessError(
    id: String,
    error: String,
    graph: String) extends Payload

  // TODO
}

private[protocol] object TraceMessages {
  sealed trait Payload extends message.Payload {
    def toMessage = message.Trace(this)
  }

  case class Start(
    graph: String,
    secret: String,
    buffersize: Option[Int]) extends Payload
}

object Message {
  private object JsonProtocol extends DefaultJsonProtocol with FamilyFormats with CustomJsonFormatHints {
    implicit val jsValueJsonFormat = JsValueFormat // to help the compiler find the implicit for RootJsonformat[Message]
    implicit val messageCoproductHint = new MessageCoproductHint("protocol", "command", "payload")

    val messageJsonFormat: RootJsonFormat[Message] = cachedImplicit
    //      val messageJsonFormat: RootJsonFormat[Message] = implicitly[RootJsonFormat[Message]]
  }

  implicit val messageJsonFormat = JsonProtocol.messageJsonFormat

  implicit def messageSubtypeJsonReader[T <: Message] = new RootJsonWriter[T] {
    def write(obj: T) = messageJsonFormat.write(obj)
  }
}
