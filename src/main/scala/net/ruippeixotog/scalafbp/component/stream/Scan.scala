package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.{ PortFlowControl, RxDefinition }
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object Scan extends Component {
  val name = "stream/Scan"
  val description = "Accumulates the elements of a stream using a function, emitting the intermediate values"
  val icon = Some("compress")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to scan")
  val initialPort = InPort[JsValue]("initial", "The initial element")
  val funcPort = InPort[String]("func", "A function with arguments (acc, x) used to create the next element")
  val inPorts = List(inPort, initialPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The generated elements")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with RxDefinition with PortFlowControl with NashornEngine {
    val in = inPort.bufferedStream

    // TODO send error after support for processerror is added
    val initial = initialPort.stream.headOption.single.map { // TODO improve this pattern
      case Some(init) => Some(init)
      case None =>
        context.stop(self)
        None
    }

    val func = funcPort.stream.map(JsFunction2(_, "acc", "x")).headOption.map {
      case Some(f) => Some(f)
      case None =>
        context.stop(self)
        None
    }

    initial.zip(func).flatMap {
      case (Some(init), Some(f)) => in.scan(init)(f)
    }.pipeTo(outPort)
  })
}
