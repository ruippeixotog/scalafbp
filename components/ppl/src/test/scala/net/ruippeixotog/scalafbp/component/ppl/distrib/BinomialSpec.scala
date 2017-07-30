package net.ruippeixotog.scalafbp.component.ppl.distrib

import thinkbayes.Pmf
import thinkbayes.extensions.Distributions._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

class BinomialSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Binomial

  "A Binomial component" should {

    "not output anything until the number of trials is known" in new ComponentInstance {
      Binomial.pPort.send(PVar.const(0.5))
      Binomial.varPort must emitNothing
    }

    "not output anything until the probability of success is known" in new ComponentInstance {
      Binomial.trialsPort.send(PVar.const(15))
      Binomial.varPort must emitNothing
    }

    "Emit a binomial distribution when scalar parameters are received as parameters" in new ComponentInstance {
      Binomial.pPort.send(PVar.const(0.5))
      Binomial.trialsPort.send(PVar.const(15))
      Binomial.varPort must emit(binomialPmf(15, 0.5).toPVar)
    }

    "Emit an updated binomial distribution when one of the parameters change" in new ComponentInstance {
      Binomial.pPort.send(PVar.const(0.5))
      Binomial.trialsPort.send(PVar.const(15))
      Binomial.varPort must emit(binomialPmf(15, 0.5).toPVar)

      Binomial.pPort.send(PVar.const(0.2))
      Binomial.varPort must emit(binomialPmf(15, 0.2).toPVar)

      Binomial.trialsPort.send(PVar.const(12))
      Binomial.varPort must emit(binomialPmf(12, 0.2).toPVar)
    }

    "Emit a binomial distribution when random variables are received as parameters" in new ComponentInstance {
      Binomial.pPort.send(PVar.const(0.5))
      Binomial.trialsPort.send(Pmf(List(10, 20)).toPVar)
      Binomial.varPort must emit(Pmf(
        binomialPmf(10, 0.5) -> 0.5,
        binomialPmf(20, 0.5) -> 0.5).mixture.toPVar)

      Binomial.pPort.send(Pmf(0.4 -> 0.4, 0.6 -> 0.6).toPVar)
      Binomial.varPort must emit(Pmf(
        binomialPmf(10, 0.4) -> 0.2,
        binomialPmf(10, 0.6) -> 0.3,
        binomialPmf(20, 0.4) -> 0.2,
        binomialPmf(20, 0.6) -> 0.3).mixture.toPVar)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
