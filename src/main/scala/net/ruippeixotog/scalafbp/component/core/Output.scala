package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._

case object Output extends Component {
  val name = "core/Output"
  val description = "Sends packets to clients as output messages"
  val icon = Some("bug")
  val isSubgraph = false

  val inPorts = List(
    InPort[JsValue]("in", "Packet to be sent as output message"))

  val outPorts = List(
    OutPort[JsValue]("out", "The sent packets"))

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition {
    inPorts.head.stream
      .doOnEach { data => context.parent ! ComponentActor.Message(data.compactPrint) }
      .pipeTo(outPorts.head)
  })
}
