package net.ruippeixotog.scalafbp.components.ppl.distrib

import akka.actor.Props
import thinkbayes.extensions.Distributions._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.PVar
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._

case object Binomial extends Component {
  val name = "ppl/distrib/Binomial"
  val description = "Emits a random variable following a binomial distribution"
  val icon = None

  val trialsPort = InPort[PVar[Int]]("trials", "The number of trials")
  val pPort = InPort[PVar[Double]]("p", "The probability of a success")
  val inPorts = List(trialsPort, pPort)

  val varPort = OutPort[PVar[Int]]("var", "The random variable")
  val outPorts = List(varPort)

  val instanceProps = Props(new ComponentActor(this) {
    val distrib = PVar.lift(binomialPmf _)
    trialsPort.stream.combineLatestWith(pPort.stream)(distrib).pipeTo(varPort)
  })
}
