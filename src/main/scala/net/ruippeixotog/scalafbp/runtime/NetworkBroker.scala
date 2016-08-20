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
  val initialRoutes: Map[PortRef, Iterable[PortRef]] =
    graph.edges.mapValues(_.keys)

  // a map containing the initial number of inwards edges of each inPort
  val initialInEdgesCount: Map[PortRef, Int] =
    initialRoutes.valuesIterator.flatten.foldLeft(Map[PortRef, Int]()) { (acc, port) =>
      acc + (port -> (acc.getOrElse(port, 0) + 1))
    }.withDefaultValue(0)

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

    // send an immediate `InPortDisconnected` message to each node actor with an unconnected inPort
    graph.nodes.iterator.foreach {
      case (nodeId, node) =>
        node.component.inPorts.foreach { inPort =>
          if (initialInEdgesCount(PortRef(nodeId, inPort.id)) == 0)
            nodeActors(nodeId) ! InPortDisconnected(inPort.id)
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
    routes: Map[PortRef, Iterable[PortRef]],
    inEdgesCount: Map[PortRef, Int]): Actor.Receive = {

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

        val newInEdgesCount = routes.getOrElse(src, Set.empty).foldLeft(inEdgesCount) { (acc, tgt) =>
          outputActor ! Disconnect(graph.id, Some(src), tgt)

          if (acc(tgt) > 1) {
            acc + (tgt -> (acc(tgt) - 1))
          } else {
            nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
            acc - tgt
          }
        }

        log.info(s"Source port $src disconnected")
        context.become(brokerBehavior(activeNodes, routes - src, newInEdgesCount))
      }

    case msg @ DisconnectInPort(tgtPort) =>
      withKnownSender(msg) { tgtNode =>
        val tgt = PortRef(tgtNode, tgtPort)

        // disconnect and remove from the table every route that targeted the disconnected port
        var hadDisconnectedRoutes = false
        val newRoutes = routes.map {
          case (src, tgts) =>
            val (disconnectedTargets, newTargets) = tgts.partition(_ == tgt)
            if (disconnectedTargets.nonEmpty) {
              outputActor ! Disconnect(graph.id, Some(src), tgt)
              hadDisconnectedRoutes = true
            }
            src -> newTargets
        }

        // remove the disconnected target from the inEdgesCount map
        val newInEdgesCount = inEdgesCount - tgt

        // send an `InPortDisconnected` (to the same actor that sent the disconnection) acknowledging the operation.
        // Do this only if an actual route was disconnected; if not, the port may have been already disconnected after
        // an initial value.
        if (hadDisconnectedRoutes)
          nodeActors(tgtNode) ! InPortDisconnected(tgtPort)

        log.info(s"Target port $tgt disconnected")
        context.become(brokerBehavior(activeNodes, newRoutes, newInEdgesCount))
      }

    case Terminated(ref) =>
      actorNodeIds.get(ref).foreach { node =>
        val (disconnectedRoutes, newRoutes) = routes.partition(_._1.node == node)

        val newInEdgesCount = disconnectedRoutes.foldLeft(inEdgesCount) {
          case (acc, (src, tgts)) =>
            tgts.foldLeft(acc) { (acc2, tgt) =>
              outputActor ! Disconnect(graph.id, Some(src), tgt)

              if (acc2(tgt) > 1) {
                acc2 + (tgt -> (acc2(tgt) - 1))
              } else {
                nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
                acc2 - tgt
              }
            }
        }

        log.info(s"Node $node terminated")
        if (activeNodes == 1) context.stop(self)
        else context.become(brokerBehavior(activeNodes - 1, newRoutes, newInEdgesCount))
      }

    case output: Output => outputActor ! output
  }

  def receive = {
    startNetwork()
    brokerBehavior(nodeActors.size, initialRoutes, initialInEdgesCount)
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
