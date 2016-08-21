package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing }
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort, SimpleComponentActor }

case object Kick extends Component {
  val name = "core/Kick"
  val description = "Generates a packet everytime a signal is received"
  val icon = Some("share")
  val isSubgraph = true

  val inPorts = List(
    InPort[JsValue]("in", "Packet to be sent"),
    InPort[Unit]("kick", "Signal to send the data packet"))

  val outPorts = List(
    OutPort[JsValue]("out", "The kicked packet"))

  val instanceProps = Props(new SimpleComponentActor(this) {
    var nextPacket = Option.empty[JsValue]

    def receive = {
      case Incoming("in", packet: JsValue) => nextPacket = Some(packet)
      case Incoming("kick", _) => nextPacket.foreach(context.parent ! Outgoing("out", _))
    }
  })
}
