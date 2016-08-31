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

  // the initial routing table mapping outgoing ports to a list of destination ports to which packets should be sent
  val initialRoutes: Map[PortRef, Set[PortRef]] =
    graph.edges.mapValues(_.keySet)

  // the reversed index of initialRoutes, for checking which ports send data to each target port
  val initialRevRoutes: Map[PortRef, Set[PortRef]] =
    initialRoutes.foldLeft(Map[PortRef, Set[PortRef]]()) {
      case (acc, (src, tgts)) =>
        tgts.foldLeft(acc) { (acc2, tgt) =>
          acc2 + (tgt -> (acc2.getOrElse(tgt, Set.empty) + src))
        }
    }

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

  def startNetwork(): Unit = {

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
            outputActor ! Error(s"Type mismatch in initial data for $tgt")
            log.error(s"Network failed: could not deserialize initial data for $tgt")
            context.stop(self)
        }
    }

    // send a Connect message to `outputActor` for each edge
    initialRoutes.foreach {
      case (src, tgts) => tgts.foreach(outputActor ! Connect(graph.id, Some(src), _))
    }

    // send an immediate `InPortDisconnected` to each node actor with an unconnected in port and a `OutPortDisconnected`
    // to each node actor with an unconnected out port
    graph.nodes.iterator.foreach {
      case (nodeId, node) =>
        node.component.inPorts.foreach { inPort =>
          if (initialRevRoutes.get(PortRef(nodeId, inPort.id)).forall(_.isEmpty))
            nodeActors(nodeId) ! InPortDisconnected(inPort.id)
        }
        node.component.outPorts.foreach { outPort =>
          if (initialRoutes.get(PortRef(nodeId, outPort.id)).forall(_.isEmpty))
            nodeActors(nodeId) ! OutPortDisconnected(outPort.id)
        }
    }
  }

  def withKnownSender(msg: Any)(f: String => Unit): Unit = {
    actorNodeIds.get(sender) match {
      case Some(srcNode) => f(srcNode)
      case None =>
        outputActor ! Error(s"Internal component error")
        log.error(s"Network failed: received message $msg by unknown sender ${sender()}")
        context.stop(self)
    }
  }

  def brokerBehavior(
    activeNodes: Int,
    routes: Map[PortRef, Set[PortRef]],
    revRoutes: Map[PortRef, Set[PortRef]]): Actor.Receive = {

    case msg @ Outgoing(srcPort, srcData) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        routes.getOrElse(src, Set.empty).foreach { tgt =>
          val jsDataOpt = serialize(src, srcData)
          val tgtDataOpt = jsDataOpt.flatMap(deserialize(tgt, _))

          (jsDataOpt, tgtDataOpt) match {
            case (Some(jsData), Some(tgtData)) =>
              log.debug(s"$src -> $tgt: $tgtData${if (srcData == tgtData) "" else s" ($srcData)"}")
              nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)
              outputActor ! Data(graph.id, Some(src), tgt, jsData)

            case (None, _) =>
              outputActor ! Error(s"Type mismatch in message from $src to $tgt")
              log.error(s"Network failed: could not serialize $srcData (sent by $src) to JSON")
              context.stop(self)

            case (Some(jsData), None) =>
              outputActor ! Error(s"Type mismatch in message from $src to $tgt")
              log.error(s"Network failed: could not deserialize $jsData (sent by $src) to a format supported by $tgt")
              context.stop(self)
          }
        }
      }

    case msg @ DisconnectOutPort(srcPort) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        // disconnect and remove from the reverse routing table every route originating from the disconnected port
        val newRevRoutes = routes.getOrElse(src, Set.empty).foldLeft(revRoutes) { (acc, tgt) =>
          outputActor ! Disconnect(graph.id, Some(src), tgt)

          val newTgtRevRoutes = acc.getOrElse(tgt, Set.empty) - src
          if (newTgtRevRoutes.isEmpty) {
            nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
            acc - tgt
          } else {
            acc + (tgt -> newTgtRevRoutes)
          }
        }
        nodeActors(src.node) ! OutPortDisconnected(src.port)

        log.info(s"Source port $src disconnected")
        context.become(brokerBehavior(activeNodes, routes - src, newRevRoutes))
      }

    case msg @ DisconnectInPort(tgtPort) =>
      withKnownSender(msg) { tgtNode =>
        val tgt = PortRef(tgtNode, tgtPort)

        // disconnect and remove from the routing table every route that targeted the disconnected port
        val newRoutes = revRoutes.getOrElse(tgt, Set.empty).foldLeft(routes) { (acc, src) =>
          outputActor ! Disconnect(graph.id, Some(src), tgt)

          val newSrcRoutes = acc.getOrElse(src, Set.empty) - tgt
          if (newSrcRoutes.isEmpty) {
            nodeActors(src.node) ! OutPortDisconnected(src.port)
            acc - src
          } else {
            acc + (src -> newSrcRoutes)
          }
        }
        nodeActors(tgt.node) ! InPortDisconnected(tgt.port)

        log.info(s"Target port $tgt disconnected")
        context.become(brokerBehavior(activeNodes, newRoutes, revRoutes - tgt))
      }

    case Terminated(ref) =>
      actorNodeIds.get(ref).foreach { node =>
        val (disconnectedRoutes, updRoutes) = routes.partition(_._1.node == node)
        val (disconnectedRevRoutes, updRevRoutes) = revRoutes.partition(_._1.node == node)

        val newRoutes = disconnectedRevRoutes.foldLeft(updRoutes) {
          case (acc, (tgt, srcs)) =>
            srcs.foldLeft(acc) { (acc2, src) =>
              outputActor ! Disconnect(graph.id, Some(src), tgt)

              val newSrcRoutes = acc2.getOrElse(src, Set.empty) - tgt
              if (newSrcRoutes.isEmpty) {
                nodeActors(src.node) ! OutPortDisconnected(src.port)
                acc2 - src
              } else {
                acc2 + (src -> newSrcRoutes)
              }
            }
        }

        val newRevRoutes = disconnectedRoutes.foldLeft(updRevRoutes) {
          case (acc, (src, tgts)) =>
            tgts.foldLeft(acc) { (acc2, tgt) =>
              outputActor ! Disconnect(graph.id, Some(src), tgt)

              val newTgtRevRoutes = acc.getOrElse(tgt, Set.empty) - src
              if (newTgtRevRoutes.isEmpty) {
                nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
                acc - tgt
              } else {
                acc + (tgt -> newTgtRevRoutes)
              }
            }
        }

        log.info(s"Node $node terminated")
        if (activeNodes == 1) context.stop(self)
        else context.become(brokerBehavior(activeNodes - 1, newRoutes, newRevRoutes))
      }

    case output: Output => outputActor ! output
  }

  def receive = {
    startNetwork()
    brokerBehavior(nodeActors.size, initialRoutes, initialRevRoutes)
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
