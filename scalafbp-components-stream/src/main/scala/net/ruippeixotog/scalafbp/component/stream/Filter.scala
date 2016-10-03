package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object Filter extends Component {
  val name = "stream/Filter"
  val description = "Emits only the elements of a stream obeying a condition"
  val icon = Some("filter")

  val inPort = InPort[JsValue]("in", "The stream to filter")
  val funcPort = InPort[String]("func", "A predicate with arguments (acc, x) used to create the next element. " +
    "While not defined, all elements pass unfiltered.")
  val inPorts = List(inPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The filtered stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) with NashornEngine {
    val defaultFunc: JsFunction = { _ => JsTrue }
    val func = defaultFunc +: funcPort.stream.map(JsFunction(_))

    inPort.stream
      .withLatestFrom(func) { (x, f) => (x, f(x)) }
      .filter { case (_, v) => v != JsFalse && v != JsNull }
      .map(_._1)
      .pipeTo(outPort)
  })
}
