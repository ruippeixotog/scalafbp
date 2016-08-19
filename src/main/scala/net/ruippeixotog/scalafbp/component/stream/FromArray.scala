package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._

object FromArray extends Component {
  val name = "stream/FromArray"
  val description = "Receives a stream of arrays and emits each of their elements"
  val icon = None
  val isSubgraph = true

  val arrayPort = InPort[List[JsValue]]("array", "The input stream of arrays")
  val inPorts = List(arrayPort)

  val outPort = OutPort[JsValue]("out", "The stream of unpacked data")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition {
    arrayPort.stream.flatMapIterable(identity).pipeTo(outPort)
  })
}
