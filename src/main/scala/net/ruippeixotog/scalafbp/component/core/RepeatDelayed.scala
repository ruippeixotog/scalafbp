package net.ruippeixotog.scalafbp.component.core

import akka.actor.{ ActorRef, Props }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort, SimpleComponentActor }
import scala.concurrent.duration._

case object RepeatDelayed extends Component {
  val name = "core/RepeatDelayed"
  val description = "Forwards packets after a set delay"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPorts = List(
    InPort[JsValue]("in", "Packet to forward with a delay"),
    InPort[Int]("delay", "Delay length (ms)"))

  val outPorts = List(
    OutPort[JsValue]("out", "Forwarded packet"))

  val instanceProps = Props(new SimpleComponentActor(this) {
    var currDelay = Option.empty[FiniteDuration]

    implicit val ec = context.dispatcher
    case class SendPacket(to: ActorRef, data: JsValue)

    def receive = {
      case Incoming("delay", delay: Int) =>
        currDelay = Some(delay.millis)

      case Incoming("in", data: JsValue) =>
        currDelay.foreach {
          context.system.scheduler.scheduleOnce(_, self, SendPacket(context.parent, data))
        }

      case SendPacket(to, data) => to ! Outgoing("out", data)
    }
  })
}
