package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object Scan extends Component {
  val name = "stream/Scan"
  val description = "Accumulates the elements of a stream using a function, emitting the intermediate values"
  val icon = Some("compress")

  val inPort = InPort[JsValue]("in", "The stream to scan")
  val initialPort = InPort[JsValue]("initial", "The initial element")
  val funcPort = InPort[String]("func", "A function with arguments (acc, x) used to create the next element")
  val inPorts = List(inPort, initialPort, funcPort)

  val outPort = OutPort[JsValue]("out", "The generated elements")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) with NashornEngine {
    val in = inPort.bufferedStream
    val initial = initialPort.stream.head
    val func = funcPort.stream.head.map(JsFunction2(_, "acc", "x"))

    initial.zip(func).flatMap { case (init, f) => in.scan(init)(f) }.pipeTo(outPort)
  })
}
