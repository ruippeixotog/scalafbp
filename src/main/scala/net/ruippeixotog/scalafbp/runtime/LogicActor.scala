package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Actor, ActorRef, Props }
import akka.event.slf4j.SLF4JLogging

import net.ruippeixotog.scalafbp.component.ComponentActor.Output
import net.ruippeixotog.scalafbp.graph.{ Graph, NetworkRunner }
import net.ruippeixotog.scalafbp.runtime.LogicActor._

class LogicActor extends Actor with SLF4JLogging {
  var graphs = Map[String, Graph]()
  var networkActors = Map[String, ActorRef]()
  var senderActors = Map[String, ActorRef]()
  var startTimes = Map[String, Long]()

  def status(id: String) = Status(
    id,
    networkActors.contains(id),
    networkActors.contains(id),
    startTimes.get(id).map { startTime => (System.currentTimeMillis() - startTime) / 1000 })

  def receive = {
    case GraphUpdated(id, graph) =>
      log.info(s"Graph $id updated: $graph")
      graphs += id -> graph
      sender() ! Ok

    case StartNetwork(id) =>
      (graphs.get(id), networkActors.get(id)) match {
        case (Some(graph), None) =>
          val actorName = s"g-$id-controller"
          networkActors += id -> context.actorOf(Props(new NetworkRunner(graph, self)), actorName)
          senderActors += id -> sender()
          startTimes += id -> System.currentTimeMillis()
          sender() ! status(id)

        case (Some(_), Some(_)) => sender() ! Error(s"Graph $id is already running")
        case (None, _) => sender() ! Error(s"Graph $id not found")
      }

    case StopNetwork(id) =>
      (graphs.get(id), networkActors.get(id)) match {
        case (_, Some(ref)) =>
          context.stop(ref)
          networkActors -= id
          senderActors -= id
          startTimes -= id
          sender() ! status(id)

        case (Some(_), _) => sender() ! Error(s"Graph $id is already stopped")
        case (None, _) => sender() ! Error(s"Graph $id not found")
      }

    case GetNetworkStatus(id) =>
      graphs.get(id) match {
        case Some(_) => sender() ! status(id)
        case None => sender() ! Error(s"Graph $id not found")
      }

    case output: Output =>
      networkActors.find(_._2 == sender()).foreach {
        case (id, _) => senderActors(id) ! output
      }
  }
}

object LogicActor {
  case class GraphUpdated(id: String, graph: Graph)
  case class StartNetwork(id: String)
  case class StopNetwork(id: String)
  case class GetNetworkStatus(id: String)

  case object Ok
  case class Error(msg: String)
  case class Status(graph: String, running: Boolean, started: Boolean, uptime: Option[Long])
}
