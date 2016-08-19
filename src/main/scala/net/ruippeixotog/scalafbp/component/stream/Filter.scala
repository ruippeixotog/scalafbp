package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json._

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.VarDefinition
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.{ NashornEngine, Var }

case object Filter extends Component {
  val name = "stream/Filter"
  val description = "Emits only the elements of a stream obeying a condition"
  val icon = Some("filter")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to filter")
  val funcPort = InPort[String]("func", "A predicate with arguments (acc, x) used to create the next element. " +
    "While not defined, all elements pass unfiltered.")
  val inPorts = List(inPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The filtered stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition with NashornEngine {
    val defaultFuncVar = Var.constant[JsFunction]({ _ => JsTrue })
    val funcVar = funcPort.value.map(JsFunction(_)).orElse(defaultFuncVar)

    funcVar.flatMap { f =>
      inPort.value.map(f).flatMap {
        case JsFalse | JsNull => Var.undefined()
        case _ => inPort.value
      }
    }.pipeTo(outPort)
  })
}
