package net.ruippeixotog.scalafbp.component.ppl.stat

import spray.json._
import thinkbayes.Pmf
import thinkbayes.extensions.Distributions._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

class MeanSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Mean

  "A Mean component" should {

    "Emit the mean of each random variable it receives" in new ComponentInstance {
      Mean.varPort.send(Pmf(JsNumber(2) -> 0.6, JsNumber(5) -> 0.4).toPVar)
      Mean.meanPort must receiveWhich(_ must beCloseTo(3.2, 0.01))

      Mean.varPort.send(binomialPmf(15, 0.4).mapKeys(JsNumber.apply).toPVar)
      Mean.meanPort must receiveWhich(_ must beCloseTo(6.0, 0.01))

      Mean.varPort.send(PVar.const(JsNumber(3)))
      Mean.meanPort must receive(3.0)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
