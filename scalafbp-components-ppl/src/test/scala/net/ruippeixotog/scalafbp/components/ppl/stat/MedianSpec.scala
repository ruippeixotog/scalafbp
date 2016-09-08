package net.ruippeixotog.scalafbp.components.ppl.stat

import spray.json._
import thinkbayes.Pmf
import thinkbayes.extensions.Distributions._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

class MedianSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Median

  "A Median component" should {

    "Emit the median of each random variable it receives" in new ComponentInstance {
      Median.varPort.send(Pmf("a".toJson -> 0.3, "b".toJson -> 0.3, "c".toJson -> 0.4).toPVar)
      Median.medianPort must receive("b".toJson)

      Median.varPort.send(binomialPmf(15, 0.4).mapKeys(_.toJson).toPVar)
      Median.medianPort must receive(6.toJson)

      Median.varPort.send(PVar.const(3.toJson))
      Median.medianPort must receive(3.toJson)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
