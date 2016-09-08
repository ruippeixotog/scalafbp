package net.ruippeixotog.scalafbp.components.ppl.stat

import akka.actor.Props
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

case object Mode extends Component {
  val name = "ppl/stat/Mode"
  val description = "Emits the mode of a random variable"
  val icon = None
  val isSubgraph = true

  val varPort = InPort[PVar[JsValue]]("var", "The random variable")
  val inPorts = List(varPort)

  val modePort = OutPort[JsValue]("mode", "The mode value")
  val outPorts = List(modePort)

  val instanceProps = Props(new ComponentActor(this) {
    varPort.stream.map(_.toPmf.mode).pipeTo(modePort)
  })
}
