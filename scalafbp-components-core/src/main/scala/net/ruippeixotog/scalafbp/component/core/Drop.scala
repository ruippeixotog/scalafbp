package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object Drop extends Component {
  val name = "core/Drop"
  val description = "Drops every packet it receives with no action"
  val icon = Some("trash-o")

  val inPorts = List(
    InPort[JsValue]("in", "Packet to be dropped"))

  val outPorts = Nil

  val instanceProps = Props(new ComponentActor(this) {})
}
