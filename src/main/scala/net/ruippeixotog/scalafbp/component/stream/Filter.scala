package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import rx.lang.scala.Observable
import spray.json._

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

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

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition with NashornEngine {
    val defaultFunc = Observable.just[JsFunction]({ _ => JsTrue })
    val isTruthy = { v: JsValue => v != JsFalse && v != JsNull }

    val func = defaultFunc ++ funcPort.stream.map(JsFunction(_))
    inPort.stream
      .withLatestFrom(func) { (x, f) => (x, isTruthy(f(x))) }
      .filter(_._2)
      .map(_._1)
      .pipeTo(outPort)
  })
}
