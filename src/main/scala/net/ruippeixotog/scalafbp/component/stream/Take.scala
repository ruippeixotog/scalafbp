package net.ruippeixotog.scalafbp.component.stream

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor.{ PortFreezing, VarDefinition }
import net.ruippeixotog.scalafbp.component._

case object Take extends Component {
  val name = "stream/Take"
  val description = "Emits only the first N elements of a stream"
  val icon = Some("filter")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "The stream to take elements from")
  val nPort = InPort[Int]("n", "The number of elements to take")
  val inPorts = List(inPort, nPort)

  val outPort = OutPort[JsValue]("out", "The taken elements")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition with PortFreezing {
    inPort.freeze()

    nPort.value.foreach { n =>
      nPort.ignore()
      inPort.unfreeze()
      inPort.value.pipeTo(outPort)
      inPort.value
        .scan(n) { (left, _) => left - 1 }
        .foreach { left => if (left == 0) context.stop(self) }
    }

    override def receive = {
      case InPortDisconnected("n") if !nPort.isIgnored => context.stop(self)
    }
  })
}
