package net.ruippeixotog.scalafbp.components.ppl.stat

import akka.actor.Props
import spray.json.JsValue
import thinkbayes.extensions.Stats._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

case object Median extends Component {
  val name = "ppl/stat/Median"
  val description = "Emits the median of a random variable"
  val icon = None

  val varPort = InPort[PVar[JsValue]]("var", "The random variable")
  val inPorts = List(varPort)

  val medianPort = OutPort[JsValue]("median", "The median value")
  val outPorts = List(medianPort)

  val instanceProps = Props(new ComponentActor(this) {
    varPort.stream.map(_.toPmf.quantile(0.5)).pipeTo(medianPort)
  })
}
