package net.ruippeixotog.scalafbp.component.core

import akka.actor.{ Actor, Props }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing }
import net.ruippeixotog.scalafbp.component.{ Component, ComponentActor, InPort, OutPort }

object Output extends Component {
  val name = "core/Output"
  val description = "Sends the data items to console.log"
  val icon = Some("bug")
  val isSubgraph = false
  val inPorts = List(InPort[JsValue]("in", "Packet to be printed through console.log"))
  val outPorts = List(OutPort[JsValue]("out", "Forwarded packet"))

  val instanceProps = Props(new Actor {
    def receive = {
      case Incoming("in", data: JsValue) =>
        sender() ! ComponentActor.Output(data.compactPrint)
        sender() ! Outgoing("out", data)
    }
  })
}
