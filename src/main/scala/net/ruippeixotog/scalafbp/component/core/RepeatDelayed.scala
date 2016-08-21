package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor.{ ActorRef, Props }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component._

case object RepeatDelayed extends Component {
  val name = "core/RepeatDelayed"
  val description = "Forwards packets after a set delay"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "Packet to forward with a delay")
  val delayPort = InPort[Long]("delay", "Delay length (ms)")
  val inPorts = List(inPort, delayPort)

  val outPort = OutPort[JsValue]("out", "Forwarded packet")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) {
    var currDelay = Option.empty[FiniteDuration]

    implicit val ec = context.dispatcher
    case class SendPacket(to: ActorRef, data: JsValue)

    def receive = {
      case Incoming("delay", delay: Long) =>
        currDelay = Some(delay.millis)

      case Incoming("in", data: JsValue) =>
        currDelay.foreach {
          context.system.scheduler.scheduleOnce(_, self, SendPacket(context.parent, data))
        }

      case SendPacket(to, data) => to ! Outgoing("out", data)
    }
  })
}
