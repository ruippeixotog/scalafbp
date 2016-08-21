package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing }
import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object MakeFunction extends Component {
  val name = "core/MakeFunction"
  val description = "Evaluates a JavaScript function each time data hits an input port " +
    "and sends the return value to the output port"

  val icon = Some("forward")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "Packet to be processed")
  val functionPort = InPort[String]("function", "Function to evaluate. The variable 'x' refers to the input; " +
    "for example, 'return x * 2' doubles the value of the input packet.'")
  val inPorts = List(inPort, functionPort)

  val outPort = OutPort[JsValue]("out", "Forwarded packet")
  val outPorts = List(outPort)

  val instanceProps = Props(new SimpleComponentActor(this) with NashornEngine {
    var currFunc = Option.empty[JsFunction]

    def receive = {
      case Incoming("function", funcStr: String) =>
        currFunc = Some(JsFunction(funcStr))

      case Incoming("in", data: JsValue) =>
        currFunc.foreach { f =>
          context.parent ! Outgoing("out", f(data))
        }
    }
  })
}
