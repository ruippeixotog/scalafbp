package net.ruippeixotog.scalafbp.component.ppl.stat

import akka.actor.Props
import spray.json.JsNumber

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

case object Mean extends Component {
  val name = "ppl/stat/Mean"
  val description = "Emits the mean of a numeric random variable"
  val icon = None

  val varPort = InPort[PVar[JsNumber]]("var", "The random variable. Must be numeric")
  val inPorts = List(varPort)

  val meanPort = OutPort[Double]("mean", "The mean value")
  val outPorts = List(meanPort)

  val instanceProps = Props(new ComponentActor(this) {
    varPort.stream.map(_.toPmf.mean).pipeTo(meanPort)
  })
}
