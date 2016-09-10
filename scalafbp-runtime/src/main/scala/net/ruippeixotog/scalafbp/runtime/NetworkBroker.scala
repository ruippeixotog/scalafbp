package net.ruippeixotog.scalafbp.runtime

import scala.util.Try

import akka.actor.{ Actor, ActorLogging, ActorRef, Terminated }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ InPort, OutPort }
import net.ruippeixotog.scalafbp.runtime.NetworkBroker._

class NetworkBroker(graph: Graph, outputActor: ActorRef) extends Actor with ActorLogging {

  override def supervisorStrategy = new NetworkBrokerSupervisorStrategy({ (child, cause) =>
    outputActor ! ProcessError(graph.id, actorNodeIds(child), cause.getMessage)
  })

  // a map from node IDs to actors running the nodes
  val nodeActors: Map[String, ActorRef] =
    graph.nodes.map {
      case (id, node) =>
        val actorName = s"node-$id".filter(_.isLetterOrDigit)
        val actorRef = context.actorOf(node.component.instanceProps, actorName)
        context.watch(actorRef)
        id -> actorRef
    }

  // the reverse index of `nodeActors`
  val actorNodeIds: Map[ActorRef, String] = nodeActors.map(_.swap)

  def serialize(portRef: PortRef, data: Any): Option[JsValue] =
    graph.nodes(portRef.node).component.outPorts.find(_.id == portRef.port).map { outPort =>
      outPort.asInstanceOf[OutPort[Any]].toJson(data)
    }

  def deserialize(portRef: PortRef, data: JsValue): Option[Any] =
    graph.nodes(portRef.node).component.inPorts.find(_.id == portRef.port).flatMap { inPort =>
      Try(inPort.asInstanceOf[InPort[Any]].fromJson(data)).toOption
    }

  def convertTo(from: PortRef, to: PortRef, data: Any): Option[Any] =
    serialize(from, data).flatMap(deserialize(to, _))

  def error(msg: String): Unit =
    error(msg.capitalize, s"Network failed: $msg")

  def error(msg: String, logMsg: String): Unit = {
    outputActor ! Error(msg)
    log.error(logMsg)
    context.stop(self)
  }

  def startNetwork(): RoutingTable = {

    val routingTable = RoutingTable(graph)
      .onRouteClosed { (src, tgt) => outputActor ! Disconnect(graph.id, Some(src), tgt) }
      .onSourceClosed { src => nodeActors(src.node) ! OutPortDisconnected(src.port) }
      .onTargetClosed { tgt => nodeActors(tgt.node) ! InPortDisconnected(tgt.port) }

    // send the initial data packets to node actors and send full [Connect, Data, Disconnect] sequence to `outputActor`
    graph.initials.foreach {
      case (tgt, Initial(jsData, _)) =>
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

    // send a Connect message to `outputActor` for each edge
    routingTable.routes.foreach {
      case (src, tgt) => outputActor ! Connect(graph.id, Some(src), tgt)
    }

    // send an immediate `InPortDisconnected` to each node actor with an unconnected in port and a `OutPortDisconnected`
    // to each node actor with an unconnected out port
    graph.nodes.iterator.foreach {
      case (nodeId, node) =>
        node.component.inPorts.foreach { inPort =>
          if (routingTable.reverseRoutes(PortRef(nodeId, inPort.id)).isEmpty)
            nodeActors(nodeId) ! InPortDisconnected(inPort.id)
        }
        node.component.outPorts.foreach { outPort =>
          if (routingTable.routes(PortRef(nodeId, outPort.id)).isEmpty)
            nodeActors(nodeId) ! OutPortDisconnected(outPort.id)
        }
    }

    routingTable
  }

  def withKnownSender(msg: Any)(f: String => Unit): Unit = {
    actorNodeIds.get(sender) match {
      case Some(srcNode) => f(srcNode)
      case None =>
        error("Internal runtime error", s"Network failed: received message $msg by unknown sender ${sender()}")
    }
  }

  def brokerBehavior(activeNodes: Int, routingTable: RoutingTable): Actor.Receive = {

    case msg @ Outgoing(srcPort, srcData) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        routingTable.routes(src).foreach { tgt =>
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

    case Terminated(ref) =>
      actorNodeIds.get(ref).foreach { node =>
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
}
