package net.ruippeixotog.scalafbp.components.ppl.distrib

import akka.actor.Props
import spray.json.JsValue
import thinkbayes.Pmf

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.PVar
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._

case object Uniform extends Component {
  val name = "ppl/distrib/Uniform"
  val description = "Emits a random variable following a uniform distribution"
  val icon = None
  val isSubgraph = true

  val elemsPort = InPort[Set[JsValue]]("elems", "The set of possible outcomes")
  val inPorts = List(elemsPort)

  val varPort = OutPort[PVar[JsValue]]("var", "The random variable")
  val outPorts = List(varPort)

  val instanceProps = Props(new ComponentActor(this) {
    elemsPort.stream.map { elems => Pmf(elems).toPVar }.pipeTo(varPort)
  })
}
