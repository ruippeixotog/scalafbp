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

  val actorNodeIds = nodeActors.map(_.swap)

  val edgeRoutes = graph.connections.flatMap {
    case ((tgt, tgtPort), Edge(src, srcPort, _)) =>
      Some((src, srcPort) -> (tgt, tgtPort))

    case ((tgt, tgtPort), IIP(data, _)) =>
      for {
        node <- graph.nodes.get(tgt)
        inPort <- node.component.inPorts.find(_.id == tgtPort)
      } nodeActors(tgt) ! Incoming(tgtPort, inPort.fromJson(data))

      None
  }

  def receive = {
    case msg @ Outgoing(port, data) =>
      actorNodeIds.get(sender()) match {
        case Some(senderId) =>
          edgeRoutes.get((senderId, port)).foreach {
            case (dest, destPort) => nodeActors(dest) ! Incoming(destPort, data)
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
