package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue
import spray.json.DefaultJsonProtocol._

import net.ruippeixotog.scalafbp.component._

case object Zip extends Component {
  val name = "stream/Zip"
  val description = "Combines elements from two streams in pairs"
  val icon = Some("dropbox")

  val in1Port = InPort[JsValue]("in1", "The first stream")
  val in2Port = InPort[JsValue]("in2", "The second stream")
  val inPorts = List(in1Port, in2Port)

  val outPort = OutPort[(JsValue, JsValue)]("out", "The zipped stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    in1Port.stream.zip(in2Port.stream)
      .doOnCompleted(context.stop(self))
      .pipeTo(outPort)
  })
}
