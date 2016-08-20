package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object Map extends Component {
  val name = "stream/Map"
  val description = "Transforms the elements of a stream using a function"

  val icon = Some("code")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to transform")
  val funcPort = InPort[String]("func", "The function with argument x to use for transformation. " +
    "While not defined, all elements pass untouched.")
  val inPorts = List(inPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The transformed stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition with NashornEngine {
    val defaultFunc: JsFunction = identity
    val func = defaultFunc +: funcPort.stream.map(JsFunction(_))
    inPort.stream.withLatestFrom(func) { (x, f) => f(x) }.pipeTo(outPort)
  })
}
