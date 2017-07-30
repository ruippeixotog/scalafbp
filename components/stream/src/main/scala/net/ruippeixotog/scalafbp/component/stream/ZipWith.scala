package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object ZipWith extends Component {
  val name = "stream/ZipWith"
  val description = "Combines elements from two streams in pairs using a selector function"
  val icon = Some("dropbox")

  val in1Port = InPort[JsValue]("in1", "The first stream")
  val in2Port = InPort[JsValue]("in2", "The second stream")
  val selectorPort = InPort[String]("selector", "A function with arguments (x1, x2) used to create the zipped element")
  val inPorts = List(in1Port, in2Port, selectorPort)

  val outPort = OutPort[JsValue]("out", "The zipped stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) with NashornEngine {
    val selector = selectorPort.stream.head.map(JsFunction2(_, "x1", "x2"))

    selector.flatMap(in1Port.stream.zipWith(in2Port.stream))
      .doOnCompleted(context.stop(self))
      .pipeTo(outPort)
  })
}
