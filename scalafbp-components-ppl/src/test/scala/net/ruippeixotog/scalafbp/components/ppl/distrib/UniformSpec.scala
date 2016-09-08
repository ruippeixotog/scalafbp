package net.ruippeixotog.scalafbp.components.ppl.distrib

import spray.json._
import thinkbayes.Pmf

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._

class UniformSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Uniform

  "A Uniform component" should {

    "Emit a uniform distribution for each element set it receives" in new ComponentInstance {
      Uniform.elemsPort.send(Set("a".toJson, "b".toJson, "c".toJson, "d".toJson))
      Uniform.varPort must receive(
        Pmf("a".toJson -> 0.25, "b".toJson -> 0.25, "c".toJson -> 0.25, "d".toJson -> 0.25).toPVar)

      Uniform.elemsPort.send(Set(5.toJson))
      Uniform.varPort must receive(Pmf(5.toJson -> 1.0).toPVar)

      Uniform.elemsPort.send(Set.empty)
      Uniform.varPort must receive(Pmf.empty[JsValue].toPVar)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
