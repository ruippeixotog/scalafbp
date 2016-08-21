package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

object ToArray extends Component {
  val name = "stream/ToArray"
  val description = "Consumes all the packets of a stream to emit a single array"
  val icon = None
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The input stream")
  val inPorts = List(inPort)

  val arrayPort = OutPort[List[JsValue]]("array", "The input stream packed as an array")
  val outPorts = List(arrayPort)

  val instanceProps = Props(new ComponentActor(this) {
    inPort.stream.toList.pipeTo(arrayPort)
  })
}
