package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor._

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case object RunTimeout extends Component {
  val name = "core/RunTimeout"
  val description = "Sends a signal after the given time"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPorts = List(
    InPort[Int]("time", "Time after which a signal will be sent (ms)"))

  val outPorts = List(
    OutPort[Unit]("out", "A signal sent after the given time"))

  val instanceProps = Props(new Actor {
    implicit val ec = context.dispatcher
    case class SendSignal(to: ActorRef)

    def receive = {
      case Incoming("time", time: Int) =>
        context.system.scheduler.scheduleOnce(time.millis, self, SendSignal(sender()))

      case SendSignal(to) =>
        to ! Outgoing("out", ())
        context.stop(self)
    }
  })
}
