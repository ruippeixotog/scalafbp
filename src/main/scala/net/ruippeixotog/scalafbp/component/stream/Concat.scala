package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._

case object Concat extends Component {
  val name = "stream/Concat"
  val description = "Emits all packets of a stream followed by all elements of another"
  val icon = Some("compress")
  val isSubgraph = true

  val in1Port = InPort[JsValue]("in1", "The first stream")
  val in2Port = InPort[JsValue]("in2", "The second stream")
  val inPorts = List(in1Port, in2Port)

  val outPort = OutPort[JsValue]("out", "The concatenated stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition {
    (in1Port.stream ++ in2Port.bufferedStream).pipeTo(outPort)
  })
}
