package net.ruippeixotog.scalafbp.runtime

import scala.util.Try

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Terminated }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ InPort, OutPort }
import net.ruippeixotog.scalafbp.runtime.GraphStore.{ EdgeKey, GraphKey, InitialKey, NodeKey }
import net.ruippeixotog.scalafbp.runtime.NetworkBroker._

class NetworkBroker(
    graph: Graph,
    dynamic: Boolean,
    outputActor: ActorRef,
    externalActorOpt: Option[ActorRef]) extends Actor with ActorLogging {

  def createNodeActor(id: String, node: Node): ActorRef = {
    val actorName = s"node-$id".filter(_.isLetterOrDigit)
    val actorRef = context.actorOf(node.component.instanceProps, actorName)
    context.watch(actorRef)
    actorRef
  }

  // a map from node IDs to actors running the nodes
  var nodeActors: Map[String, ActorRef] =
    graph.nodes.map { case (id, node) => id -> createNodeActor(id, node) }

  // the reverse index of `nodeActors`
  def actorNodeIds(nodeActor: ActorRef): Option[String] =
    nodeActors.find(_._2 == nodeActor).map(_._1)

  override def supervisorStrategy = new NetworkBrokerSupervisorStrategy({ (child, cause) =>
    outputActor ! ProcessError(graph.id, actorNodeIds(child).get, cause.getMessage)
  })

  def serialize(portRef: PortRef, data: Any): Option[JsValue] =
    graph.outPortInfo(portRef).map { outPort =>
      outPort.asInstanceOf[OutPort[Any]].toJson(data)
    }

  def deserialize(portRef: PortRef, data: JsValue): Option[Any] =
    graph.inPortInfo(portRef).flatMap { inPort =>
      Try(inPort.asInstanceOf[InPort[Any]].fromJson(data)).toOption
    }

  def convertTo(from: PortRef, to: PortRef, data: Any): Option[Any] =
    serialize(from, data).flatMap(deserialize(to, _))

  def sendInitial(tgt: PortRef, jsData: JsValue): Unit = {
    deserialize(tgt, jsData) match {
      case Some(tgtData) =>
        log.debug(s"DATA -> $tgt: $tgtData")
        nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)
        outputActor ! Connect(graph.id, None, tgt)
        outputActor ! Data(graph.id, None, tgt, jsData)
        outputActor ! Disconnect(graph.id, None, tgt)

      case None =>
        error(s"could not deserialize initial data for $tgt")
    }
  }

  def sendExternal(msg: ComponentMessage) =
    externalActorOpt.foreach(_ ! External(msg))

  def error(msg: String): Unit =
    error(msg.capitalize, s"Network failed: $msg")

  def error(msg: String, logMsg: String): Unit = {
    outputActor ! Error(msg)
    log.error(logMsg)
    context.stop(self)
  }

  def startNetwork(): RoutingTable = {

    // send the initial data packets to node actors and send full [Connect, Data, Disconnect] sequence to `outputActor`
    graph.initials.foreach {
      case (tgt, Initial(data, _)) => sendInitial(tgt, data)
    }

    val noop = { _: PortRef => }

    RoutingTable()
      .onRouteOpened { (src, tgt) => outputActor ! Connect(graph.id, Some(src), tgt) }
      .onRouteClosed { (src, tgt) => outputActor ! Disconnect(graph.id, Some(src), tgt) }
      .onSourceClosed(if (dynamic) noop else {
        case ExternalPortRef(publicTgt) => sendExternal(DisconnectInPort(publicTgt))
        case src => nodeActors(src.node) ! OutPortDisconnected(src.port)
      })
      .onTargetClosed(if (dynamic) noop else {
        case ExternalPortRef(publicSrc) => sendExternal(DisconnectOutPort(publicSrc))
        case tgt => nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
      })
      .loadGraph(graph, externalActorOpt.map(_ => ExternalPortRef.NodeId))
  }

  def withKnownSender(msg: Any)(f: String => Unit): Unit = {
    actorNodeIds(sender) match {
      case Some(srcNode) => f(srcNode)
      case None =>
        error("Internal runtime error", s"Network failed: received message $msg by unknown sender ${sender()}")
    }
  }

  def handleStoreEvent[A](event: GraphStore.Response[A], activeNodes: Int, routingTable: RoutingTable) = event match {

    case GraphStore.Created(key: NodeKey, node: Node) =>
      nodeActors += key.nodeId -> createNodeActor(key.nodeId, node)
      log.info(s"New node ${key.nodeId} started")
      context.become(brokerBehavior(activeNodes + 1, routingTable))

    case GraphStore.Deleted(NodeKey(_, nodeId), _) =>
      // `activeNodes` is only decremented when the `Terminated` message is received
      nodeActors.get(nodeId).foreach(context.stop)

    case GraphStore.Created(EdgeKey(_, src, tgt), _) =>
      context.become(brokerBehavior(activeNodes, routingTable.openRoute(src, tgt)))

    case GraphStore.Deleted(EdgeKey(_, src, tgt), _) =>
      context.become(brokerBehavior(activeNodes, routingTable.closeRoute(src, tgt)))

    case GraphStore.Created(InitialKey(_, tgt), Initial(data, _)) =>
      sendInitial(tgt, data)

    case GraphStore.Deleted(GraphKey(_), _) =>
      context.stop(self)

    case _ => // nothing to do here
  }

  def handleExternalMessage(
    msg: ComponentMessage,
    externalActor: ActorRef,
    activeNodes: Int,
    routingTable: RoutingTable) = msg match {

    case Incoming(publicTgt, tgtData) =>
      val tgt = graph.publicIn(publicTgt).internal
      nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)

    case InPortDisconnected(publicTgt) =>
      val src = ExternalPortRef(publicTgt)
      val tgt = graph.publicIn(publicTgt).internal
      context.become(brokerBehavior(activeNodes, routingTable.closeRoute(src, tgt)))

    case OutPortDisconnected(publicSrc) =>
      val src = graph.publicOut(publicSrc).internal
      val tgt = ExternalPortRef(publicSrc)
      context.become(brokerBehavior(activeNodes, routingTable.closeRoute(src, tgt)))

    case _ =>
      log.warning(s"Broker received unexpected external message $msg")
  }

  def brokerBehavior(activeNodes: Int, routingTable: RoutingTable): Actor.Receive = {

    case msg @ Outgoing(srcPort, srcData) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        routingTable.routes(src).foreach {
          case ExternalPortRef(publicSrc) =>
            sendExternal(Outgoing(publicSrc, srcData))

          case tgt =>
            val jsDataOpt = serialize(src, srcData)
            val tgtDataOpt = jsDataOpt.flatMap(deserialize(tgt, _))

            (jsDataOpt, tgtDataOpt) match {
              case (Some(jsData), Some(tgtData)) =>
                log.debug(s"$src -> $tgt: $tgtData${if (srcData == tgtData) "" else s" ($srcData)"}")
                nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)
                outputActor ! Data(graph.id, Some(src), tgt, jsData)

              case (None, _) =>
                error(s"could not serialize $srcData (sent by $src) to JSON")

              case (Some(jsData), None) =>
                error(s"could not deserialize $jsData (sent by $src) to a format supported by $tgt")
            }
        }
      }

    case msg @ DisconnectOutPort(srcPort) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)
        log.info(s"Source port $src disconnected")
        context.become(brokerBehavior(activeNodes, routingTable.closeSource(src)))
      }

    case msg @ DisconnectInPort(tgtPort) =>
      withKnownSender(msg) { tgtNode =>
        val tgt = PortRef(tgtNode, tgtPort)
        log.info(s"Target port $tgt disconnected")
        context.become(brokerBehavior(activeNodes, routingTable.closeTarget(tgt)))
      }

    case External(msg) if externalActorOpt.nonEmpty =>
      handleExternalMessage(msg, externalActorOpt.get, activeNodes, routingTable)

    case GraphStore.Event(ev: GraphStore.Response[_]) if dynamic =>
      handleStoreEvent(ev, activeNodes, routingTable)

    case Terminated(ref) =>
      actorNodeIds(ref).foreach { node =>
        log.info(s"Node $node terminated")
        if (activeNodes == 1) context.stop(self)
        else context.become(brokerBehavior(activeNodes - 1, routingTable.closeNode(node)))
      }

    case output: Output => outputActor ! output
  }

  def receive = {
    val routingTable = startNetwork()
    brokerBehavior(nodeActors.size, routingTable)
  }
}

object NetworkBroker {

  def props(graph: Graph, dynamic: Boolean, outputActor: ActorRef): Props =
    Props(new NetworkBroker(graph, dynamic, outputActor, None)).withDispatcher("akka.fbp-network-dispatcher")

  def props(graph: Graph, dynamic: Boolean, outputActor: ActorRef, externalActor: ActorRef) =
    Props(new NetworkBroker(graph, dynamic, outputActor, Some(externalActor)))
      .withDispatcher("akka.fbp-network-dispatcher")

  sealed trait Activity {
    def graph: String
    def src: Option[PortRef]
    def tgt: PortRef
  }

  case class Connect(graph: String, src: Option[PortRef], tgt: PortRef) extends Activity
  case class Disconnect(graph: String, src: Option[PortRef], tgt: PortRef) extends Activity
  case class Data(graph: String, src: Option[PortRef], tgt: PortRef, data: JsValue) extends Activity

  case class Error(msg: String)
  case class ProcessError(graph: String, node: String, msg: String)

  case class External(msg: ComponentMessage)

  object ExternalPortRef {
    val NodeId = ""
    def apply(port: String) = PortRef(NodeId, port)
    def unapply(portRef: PortRef) = if (portRef.node == NodeId) Some(portRef.port) else None
  }
}
