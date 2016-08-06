package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort, SimpleComponentActor }

case object Merge extends Component {
  val name = "core/Merge"
  val description = "Merges two inputs into one"
  val icon = Some("compress")
  val isSubgraph = true

  val inPorts = List(
    InPort[JsValue]("in1", "Packets from the first input"),
    InPort[JsValue]("in2", "Packets from the second input"))

  val outPorts = List(
    OutPort[JsValue]("out", "Packets from both the first and the second port"))

  val instanceProps = Props(new SimpleComponentActor(this) {
    def receive = {
      case Incoming(_, data) => sender() ! Outgoing("out", data)
    }
  })
}
