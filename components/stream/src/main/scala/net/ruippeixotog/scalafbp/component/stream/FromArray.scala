package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object FromArray extends Component {
  val name = "stream/FromArray"
  val description = "Receives a stream of arrays and emits each of their elements"
  val icon = None

  val arrayPort = InPort[List[JsValue]]("array", "The input stream of arrays")
  val inPorts = List(arrayPort)

  val outPort = OutPort[JsValue]("out", "The stream of unpacked data")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    arrayPort.stream.flatMapIterable(identity).pipeTo(outPort)
  })
}
