package net.ruippeixotog.scalafbp.runtime

import akka.actor.SupervisorStrategy.{ Decider, Stop }
import akka.actor._
import NetworkBrokerSupervisorStrategy._

class NetworkBrokerSupervisorStrategy(onError: (ActorRef, Throwable) => Unit)
    extends OneForOneStrategy()(stoppingDecider) {

  override def handleFailure(
    context: ActorContext,
    child: ActorRef,
    cause: Throwable,
    stats: ChildRestartStats,
    children: Iterable[ChildRestartStats]): Boolean = {
    cause match {
      case _: ActorKilledException | _: DeathPactException =>
      case ex: Exception => onError(child, cause)
    }
    super.handleFailure(context, child, cause, stats, children)
  }

  override val loggingEnabled = false
}

object NetworkBrokerSupervisorStrategy {
  def stoppingDecider: Decider = {
    case _: Exception => Stop
  }
}
