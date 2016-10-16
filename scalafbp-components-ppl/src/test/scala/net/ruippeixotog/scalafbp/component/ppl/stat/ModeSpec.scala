package net.ruippeixotog.scalafbp.component.ppl.stat

import spray.json._
import thinkbayes.Pmf
import thinkbayes.extensions.Distributions._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

class ModeSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Mode

  "A Mode component" should {

    "Emit the mode of each random variable it receives" in new ComponentInstance {
      Mode.varPort.send(Pmf("a".toJson -> 0.3, "b".toJson -> 0.3, "c".toJson -> 0.4).toPVar)
      Mode.modePort must emit("c".toJson)

      Mode.varPort.send(binomialPmf(15, 0.4).mapKeys(_.toJson).toPVar)
      Mode.modePort must emit(6.toJson)

      Mode.varPort.send(PVar.const(3.toJson))
      Mode.modePort must emit(3.toJson)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
