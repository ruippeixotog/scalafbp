package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component._

case object Output extends Component {
  val name = "core/Output"
  val description = "Sends the data items to console.log"
  val icon = Some("bug")
  val isSubgraph = false
  val inPorts = List(InPort[JsValue]("in", "Packet to be printed through console.log"))
  val outPorts = List(OutPort[JsValue]("out", "Forwarded packet"))

  val instanceProps = Props(new SimpleComponentActor(this) {
    def receive = {
      case Incoming("in", data: JsValue) =>
        sender() ! ComponentActor.Message(data.compactPrint)
        sender() ! Outgoing("out", data)
    }
  })
}
