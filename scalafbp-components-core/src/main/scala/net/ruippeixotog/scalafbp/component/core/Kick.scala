package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object Kick extends Component {
  val name = "core/Kick"
  val description = "Generates a packet everytime a signal is received"
  val icon = Some("share")

  val inPort = InPort[JsValue]("in", "Packet to be sent")
  val kickPort = InPort[Unit]("kick", "Signal to send the data packet")
  val inPorts = List(inPort, kickPort)

  val outPort = OutPort[JsValue]("out", "The kicked packet")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    kickPort.stream.withLatestFrom(inPort.stream) { (_, in) => in }.pipeTo(outPort)
  })
}
