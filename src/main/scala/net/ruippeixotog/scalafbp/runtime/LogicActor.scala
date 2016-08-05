package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Actor, ActorRef, Props }
import akka.event.slf4j.SLF4JLogging

import net.ruippeixotog.scalafbp.runtime.LogicActor._
import net.ruippeixotog.scalafbp.runtime.NetworkController.{ GetStatus, Start, Stop }

class LogicActor extends Actor with SLF4JLogging {
  var graphs = Map[String, Graph]()
  var runnerActors = Map[String, ActorRef]()

  def runnerActorFor(id: String) = runnerActors.get(id) match {
    case Some(ref) => ref
    case None =>
      val ref = context.actorOf(Props(new NetworkController(id)), s"g-$id-runner")
      runnerActors += id -> ref
      ref
  }

  def receive = {
    case GraphUpdated(id, graph) =>
      log.info(s"Graph $id updated: $graph")
      graphs += id -> graph
      sender() ! Ok

    case StartNetwork(id, outputActor) =>
      graphs.get(id) match {
        case Some(graph) =>
          val runnerActor = runnerActorFor(id)
          runnerActor ! Start(graph, outputActor)
          runnerActor.tell(GetStatus, sender())

        case None => sender() ! Error(s"Graph $id not found")
      }

    case StopNetwork(id) =>
      graphs.get(id) match {
        case Some(_) =>
          val runnerActor = runnerActorFor(id)
          runnerActor ! Stop
          runnerActor.tell(GetStatus, sender())

        case None => sender() ! Error(s"Graph $id not found")
      }

    case GetNetworkStatus(id) =>
      graphs.get(id) match {
        case Some(_) => runnerActorFor(id).tell(GetStatus, sender())
        case None => sender() ! Error(s"Graph $id not found")
      }
  }
}

object LogicActor {
  case class GraphUpdated(id: String, graph: Graph)
  case class StartNetwork(id: String, outputActor: ActorRef)
  case class StopNetwork(id: String)
  case class GetNetworkStatus(id: String)

  case object Ok
  case class Error(msg: String)
}
