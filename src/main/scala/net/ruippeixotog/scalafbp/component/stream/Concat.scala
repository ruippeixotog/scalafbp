package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor.{ PortFreezing, VarDefinition }
import net.ruippeixotog.scalafbp.component._

case object Concat extends Component {
  val name = "stream/Concat"
  val description = "Emits all packets of a stream followed by all elements of another"
  val icon = Some("compress")
  val isSubgraph = true

  val in1Port = InPort[JsValue]("in1", "The first stream")
  val in2Port = InPort[JsValue]("in2", "The second stream")
  val inPorts = List(in1Port, in2Port)

  val outPort = OutPort[JsValue]("out", "The concatenated stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition with PortFreezing {
    in1Port.value.pipeTo(outPort)
    in2Port.value.pipeTo(outPort)
    in2Port.freeze()

    override def receive = {
      case InPortDisconnected("in1") => in2Port.unfreeze()
    }
  })
}
