package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor._

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case object RunInterval extends Component {
  val name = "core/RunInterval"
  val description = "Send a packet at the given interval"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPorts = List(
    InPort[Int]("interval", "Interval at which output packets are emitted (ms)"),
    InPort[Unit]("stop", "Stop the emission"))

  val outPorts = List(
    OutPort[Unit]("out", "A signal to be sent at the given interval"))

  val instanceProps = Props(new Actor {
    var nextSignalSchedule = Option.empty[Cancellable]

    implicit val ec = context.dispatcher
    case class SendPacket(to: ActorRef)

    def receive = {
      case Incoming("interval", interval: Int) =>
        nextSignalSchedule.map(_.cancel())
        nextSignalSchedule = Some(context.system.scheduler.schedule(
          interval.millis, interval.millis, self, SendPacket(sender())))

      case Incoming("stop", _) => context.stop(self)

      case SendPacket(to) => to ! Outgoing("out", ())
    }
  })
}
