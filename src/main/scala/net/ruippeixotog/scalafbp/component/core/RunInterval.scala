package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor._
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case object RunInterval extends Component {
  val name = "core/RunInterval"
  val description = "Send a packet at the given interval"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPorts = List(
    InPort[Int]("interval", "Interval at which output packets are emitted (ms)"),
    InPort[JsValue]("in", "The packet to be sent"),
    InPort[JsValue]("stop", "Stop the emission"))

  val outPorts = List(
    OutPort[JsValue]("out", "The packet to be sent at the given interval"))

  val instanceProps = Props(new Actor {
    var currInterval = Option.empty[FiniteDuration]
    var nextPacket = Option.empty[JsValue]
    var nextPacketSchedule = Option.empty[Cancellable]

    implicit val ec = context.dispatcher
    case class SendPacket(to: ActorRef)

    def receive = {
      case Incoming("interval", interval: Int) =>
        nextPacketSchedule.map(_.cancel())
        currInterval = Some(interval.millis)
        nextPacketSchedule = Some(context.system.scheduler.schedule(
          interval.millis, interval.millis, self, SendPacket(sender())))

      case Incoming("in", packet: JsValue) => nextPacket = Some(packet)
      case Incoming("stop", _) => context.stop(self)

      case SendPacket(to) => nextPacket.foreach(to ! Outgoing("out", _))
    }
  })
}
