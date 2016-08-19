package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import rx.lang.scala.Observable
import spray.json.{ JsArray, JsValue }

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object MapConcat extends Component {
  val name = "stream/MapConcat"
  val description = "Transforms the elements of a stream into arrays of elements and flatterns them"

  val icon = Some("code")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to transform")
  val funcPort = InPort[String]("func", "The function with argument x to use for transformation. " +
    "Must return an array. While not defined, all elements pass untouched.")
  val inPorts = List(inPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The transformed stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition with NashornEngine {
    val defaultFunc = Observable.just[JsFunction](JsArray(_))

    val func = defaultFunc ++ funcPort.stream.map(JsFunction(_))
    inPort.stream.withLatestFrom(func) { (x, f) => f(x) }.flatMapIterable {
      case JsArray(elems) => elems
      case _ => Nil // TODO send error after support for processerror is added
    }.pipeTo(outPort)
  })
}
