package net.ruippeixotog.scalafbp.graph

import akka.actor.{ Actor, ActorLogging, ActorRef, Terminated }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ InPort, OutPort }
import net.ruippeixotog.scalafbp.runtime.LogicActor.Error

class NetworkBroker(graph: Graph, outputActor: ActorRef) extends Actor with ActorLogging {

  val nodeActors: Map[String, ActorRef] =
    graph.nodes.map {
      case (id, node) =>
        val actorName = s"node-$id".filter(_.isLetterOrDigit)
        val actorRef = context.actorOf(node.component.instanceProps, actorName)
        context.watch(actorRef)
        id -> actorRef
    }

  val actorNodeIds: Map[ActorRef, String] = nodeActors.map(_.swap)

  graph.connections.collect {
    case (tgt, IIP(jsData, _)) =>
      deserialize(tgt, jsData) match {
        case Some(tgtData) =>
          log.info(s"[IIP -> $tgt] $tgtData")
          nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)
          nodeActors(tgt.node) ! InPortDisconnected(tgt.port)

        case None =>
          outputActor ! Error(s"Type mismatch in initial data for $tgt")
          log.error(s"Network failed: could not deserialize initial data for $tgt")
          context.stop(self)
      }
  }

  def serialize(portRef: PortRef, data: Any): Option[JsValue] =
    graph.nodes(portRef.node).component.outPorts.find(_.id == portRef.port).map { outPort =>
      outPort.asInstanceOf[OutPort[Any]].toJson(data)
    }

  def deserialize(portRef: PortRef, data: JsValue): Option[Any] =
    graph.nodes(portRef.node).component.inPorts.find(_.id == portRef.port).map { inPort =>
      inPort.asInstanceOf[InPort[Any]].fromJson(data)
    }

  def convertTo(from: PortRef, to: PortRef, data: Any): Option[Any] =
    serialize(from, data).flatMap(deserialize(to, _))

  def withKnownSender(msg: Any)(f: String => Unit): Unit = {
    actorNodeIds.get(sender) match {
      case Some(srcNode) => f(srcNode)
      case None =>
        outputActor ! Error(s"Internal component error")
        log.error(s"Network failed: received message $msg by unknown sender ${sender()}")
        context.stop(self)
    }
  }

  def brokerBehavior(activeNodes: Int, routes: Map[PortRef, Seq[PortRef]]): Actor.Receive = {
    case msg @ Outgoing(srcPort, srcData) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        routes(src).foreach { tgt =>
          convertTo(src, tgt, srcData) match {
            case Some(tgtData) =>
              log.info(s"[$src -> $tgt] $tgtData ${if (srcData == tgtData) "" else s"($srcData)"}")
              nodeActors(tgt.node) ! Incoming(tgt.port, tgtData)

            case None =>
              outputActor ! Error(s"Type mismatch in message from $src to $tgt")
              log.error(s"Network failed: could not convert data between $src and $tgt")
              context.stop(self)
          }
        }
      }

    case msg @ DisconnectOutPort(srcPort) =>
      withKnownSender(msg) { srcNode =>
        val src = PortRef(srcNode, srcPort)

        routes(src).foreach { tgt =>
          nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
        }

        log.info(s"Port $src disconnected")
        context.become(brokerBehavior(activeNodes, routes - src))
      }

    case Terminated(ref) =>
      actorNodeIds.get(ref).foreach { node =>
        val (disconnectedRoutes, newRoutes) = routes.partition(_._1.node == node)

        disconnectedRoutes.valuesIterator.flatten.foreach { tgt =>
          nodeActors(tgt.node) ! InPortDisconnected(tgt.port)
        }

        log.info(s"Node $node terminated")
        if (activeNodes == 1) context.stop(self)
        else context.become(brokerBehavior(activeNodes - 1, newRoutes))
      }

    case output: Output => outputActor ! output
  }

  def receive = {
    val edgeRoutes: Map[PortRef, Seq[PortRef]] =
      graph.connections.toSeq.collect { case (tgt, Edge(src, _)) => src -> tgt }
        .groupBy(_._1).mapValues(_.map(_._2)).withDefaultValue(Nil)

    brokerBehavior(nodeActors.size, edgeRoutes)
  }
}
