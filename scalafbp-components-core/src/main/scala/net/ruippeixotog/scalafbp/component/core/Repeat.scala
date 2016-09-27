package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object Repeat extends Component {
  val name = "core/Repeat"
  val description = "Forwards packets in the same way it receives them"
  val icon = Some("forward")

  val inPorts = List(
    InPort[JsValue]("in", "Packet to forward"))

  val outPorts = List(
    OutPort[JsValue]("out", "Forwarded packet"))

  val instanceProps = Props(new ComponentActor(this) {
    inPorts.head.stream.pipeTo(outPorts.head)
  })
}
