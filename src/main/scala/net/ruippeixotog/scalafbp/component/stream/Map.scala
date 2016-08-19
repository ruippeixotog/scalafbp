package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.VarDefinition
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.{ NashornEngine, Var }

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

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition with NashornEngine {
    val defaultFuncVar = Var.constant[JsFunction](identity)
    val funcVar = funcPort.value.map(JsFunction(_)).orElse(defaultFuncVar)

    funcVar.flatMap { f => inPort.value.map(f) }.pipeTo(outPort)
  })
}
