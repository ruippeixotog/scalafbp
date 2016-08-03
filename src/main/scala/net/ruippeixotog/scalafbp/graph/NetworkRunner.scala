package net.ruippeixotog.scalafbp.graph

import akka.actor.{ Actor, ActorLogging, ActorRef }

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing, Output }

class NetworkRunner(graph: Graph, controller: ActorRef) extends Actor with ActorLogging {
  val nodeActors: Map[String, ActorRef] =
    graph.nodes.map {
      case (id, node) =>
        val actorName = s"g-${graph.id}-node-$id".filter(_.isLetterOrDigit)
        id -> context.actorOf(node.component.instanceProps, actorName)
    }

  val actorNodeIds: Map[ActorRef, String] = nodeActors.map(_.swap)

  val edgeRoutes: Map[PortRef, PortRef] =
    graph.connections.flatMap {
      case (tgt, Edge(src, _)) => Some(src -> tgt)

      case (tgt, IIP(data, _)) =>
        for {
          node <- graph.nodes.get(tgt.node)
          inPort <- node.component.inPorts.find(_.id == tgt.port)
        } nodeActors(tgt.node) ! Incoming(tgt.port, inPort.fromJson(data))

        None
    }

  override def preStart() = {
    log.info(s"Started network for graph ${graph.id}")
  }

  def receive = {
    case msg @ Outgoing(port, data) =>
      actorNodeIds.get(sender()) match {
        case Some(node) =>
          edgeRoutes.get(PortRef(node, port)).foreach {
            case PortRef(destNode, destPort) => nodeActors(destNode) ! Incoming(destPort, data)
          }

        case None =>
          log.warning(s"Sender ${sender()} not recognized. Message $msg will be ignored")
      }

    case output: Output => controller ! output
  }
}

object NetworkRunner {
  case class Status(running: Boolean, started: Boolean, uptime: Long)
}
