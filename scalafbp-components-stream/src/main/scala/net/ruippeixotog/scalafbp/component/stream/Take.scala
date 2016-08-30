package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object Take extends Component {
  val name = "stream/Take"
  val description = "Emits only the first N elements of a stream"
  val icon = Some("filter")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to take elements from")
  val nPort = InPort[Int]("n", "The number of elements to take")
  val inPorts = List(inPort, nPort)

  val outPort = OutPort[JsValue]("out", "The taken elements")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    val in = inPort.bufferedStream
    val toTake = nPort.stream.head

    toTake.flatMap(in.take).doOnCompleted(context.stop(self)).pipeTo(outPort)
  })
}
