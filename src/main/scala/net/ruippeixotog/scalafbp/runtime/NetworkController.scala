package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Status => _, _ }

import net.ruippeixotog.scalafbp.runtime.NetworkController._

class NetworkController(graphId: String) extends Actor with ActorLogging with Stash {

  def notRunningBehavior(stopped: Boolean): Receive = {
    case Start(graph, outputActor) =>
      log.info(s"Started network of graph $graph")
      val brokerActor = context.actorOf(
        Props(new NetworkBroker(graph, outputActor)).withDispatcher("akka.fbp-network-dispatcher"), "broker")

      context.watch(brokerActor)
      context.watch(outputActor)
      context.become(runningBehavior(brokerActor, outputActor))

    case GetStatus => sender() ! Status(graphId, false, !stopped, None)
  }

  def runningBehavior(
    brokerActor: ActorRef,
    outputActor: ActorRef,
    startTime: Long = System.currentTimeMillis()): Receive = {

    case GetStatus =>
      sender() ! Status(graphId, true, true, Some((System.currentTimeMillis() - startTime) / 1000))

    case Stop =>
      log.info(s"Stopping network of graph $graphId...")
      context.stop(brokerActor)
      context.become(waitingForStopBehavior(brokerActor, outputActor, startTime))

    case Terminated(`brokerActor`) =>
      log.info(s"Finished network of graph $graphId")
      outputActor ! Finished(graphId, System.currentTimeMillis(), System.currentTimeMillis() - startTime / 1000)
      context.become(notRunningBehavior(false))

    case Terminated(`outputActor`) =>
      log.info(s"The connection with client that started network for graph $graphId was closed. Stopping network...")
      context.stop(brokerActor)
      context.become(waitingForStopBehavior(brokerActor, outputActor, startTime))
  }

  def waitingForStopBehavior(brokerActor: ActorRef, outputActor: ActorRef, startTime: Long): Receive = {

    case Terminated(`brokerActor`) =>
      log.info(s"Stopped network of graph $graphId")
      unstashAll()
      context.become(notRunningBehavior(true))

    case msg => stash()
  }

  def receive = notRunningBehavior(false)
}

object NetworkController {
  case class Start(graph: Graph, outputActor: ActorRef)
  case object GetStatus
  case object Stop

  case class Status(graph: String, running: Boolean, started: Boolean, uptime: Option[Long])
  case class Finished(graph: String, time: Long, uptime: Long)
}
