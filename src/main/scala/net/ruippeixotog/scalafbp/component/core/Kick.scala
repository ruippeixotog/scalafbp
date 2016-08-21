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

  val inPort = InPort[JsValue]("in", "Packet to be sent")
  val kickPort = InPort[Unit]("kick", "Signal to send the data packet")
  val inPorts = List(inPort, kickPort)

  val outPort = OutPort[JsValue]("out", "The kicked packet")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) {
    var nextPacket = Option.empty[JsValue]

    def receive = {
      case Incoming("in", packet: JsValue) => nextPacket = Some(packet)
      case Incoming("kick", _) => nextPacket.foreach(context.parent ! Outgoing("out", _))
    }
  })
}
