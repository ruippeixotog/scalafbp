package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._

case object Drop extends Component {
  val name = "stream/Drop"
  val description = "Drops the first N elements of a stream and emits the remaining ones"
  val icon = Some("filter")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to drop elements from")
  val nPort = InPort[Int]("n", "The number of elements to drop")
  val inPorts = List(inPort, nPort)

  val outPort = OutPort[JsValue]("out", "The elements that were not dropped")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition {
    val in = inPort.bufferedStream
    val toDrop = nPort.stream.head

    toDrop.flatMap(in.drop).pipeTo(outPort)
  })
}
