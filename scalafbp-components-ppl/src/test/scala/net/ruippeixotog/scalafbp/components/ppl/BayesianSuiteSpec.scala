package net.ruippeixotog.scalafbp.components.ppl

import org.specs2.matcher.Matcher
import spray.json._
import thinkbayes.{ Pmf, Suite }

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._

class BayesianSuiteSpec extends ComponentSpec with AutoTerminateSpec {
  val component = BayesianSuite

  val prior = Pmf(List(4, 6, 8, 12, 20))
  val likelihoodJs = "return hypo < data ? 0 : 1 / hypo"

  val diceSuite = Suite[Int, Int](prior) { (data, hypo) => if (hypo < data) 0.0 else 1.0 / hypo }

  implicit class PmfToJson[K](val pmf: Pmf[K]) {
    def toJsonPmf(implicit jf: JsonFormat[K]) = pmf.mapKeys(_.toJson)
  }

  def beCloseTo[K](expected: Pmf[K], delta: Double = 0.01): Matcher[Pmf[K]] = { actual: Pmf[K] =>
    foreach(actual.keySet ++ expected.keySet) { k =>
      actual.prob(k) aka s"The probability for $k" must beCloseTo(expected.prob(k), delta)
    }
  }

  "A BayesianSuite component" should {

    "not output anything until the prior is known" in new ComponentInstance {
      BayesianSuite.lhoodPort.send(likelihoodJs)
      BayesianSuite.obsPort.send(JsNumber(4))
      BayesianSuite.posteriorPort must receiveNothing
    }

    "not output anything until the likelihood function is known" in new ComponentInstance {
      BayesianSuite.priorPort.send(prior.toJsonPmf)
      BayesianSuite.obsPort.send(JsNumber(4))
      BayesianSuite.posteriorPort must receiveNothing
    }

    "output the prior first when both initial parameters are known" in new ComponentInstance {
      BayesianSuite.priorPort.send(prior.toJsonPmf)
      BayesianSuite.lhoodPort.send(likelihoodJs)
      BayesianSuite.posteriorPort must receive(prior.toJsonPmf)
    }

    "output the updated posterior as new observations arrive" in new ComponentInstance {
      BayesianSuite.priorPort.send(prior.toJsonPmf)
      BayesianSuite.lhoodPort.send(likelihoodJs)
      BayesianSuite.posteriorPort must receive(prior.toJsonPmf)

      BayesianSuite.obsPort.send(JsNumber(6))
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6).pmf.toJsonPmf))

      BayesianSuite.obsPort.send(JsNumber(4))
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6, 4).pmf.toJsonPmf))

      BayesianSuite.obsPort.send(JsNumber(8))
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6, 4, 8).pmf.toJsonPmf))
    }

    "consider observations arrived before the initial parameters are known" in new ComponentInstance {
      BayesianSuite.obsPort.send(JsNumber(6))
      BayesianSuite.obsPort.send(JsNumber(4))
      BayesianSuite.obsPort.send(JsNumber(8))

      BayesianSuite.priorPort.send(prior.toJsonPmf)
      BayesianSuite.lhoodPort.send(likelihoodJs)
      BayesianSuite.posteriorPort must receive(prior.toJsonPmf)
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6).pmf.toJsonPmf))
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6, 4).pmf.toJsonPmf))
      BayesianSuite.posteriorPort must receiveWhich(_ must beCloseTo(diceSuite.observed(6, 4, 8).pmf.toJsonPmf))
    }

    "terminate with a ProcessError if no data is received on the prior port" in new ComponentInstance {
      BayesianSuite.priorPort.close()
      this must terminateWithProcessError()
    }

    "terminate with a ProcessError if no data is received on the function port" in new ComponentInstance {
      BayesianSuite.lhoodPort.close()
      this must terminateWithProcessError()
    }

    "terminate when all ports are closed after some messages are received" in new ComponentInstance {
      BayesianSuite.priorPort.send(prior.toJsonPmf)
      BayesianSuite.lhoodPort.send(likelihoodJs)
      BayesianSuite.obsPort.send(JsNumber(3))
      BayesianSuite.priorPort.close()
      BayesianSuite.lhoodPort.close()
      BayesianSuite.obsPort.close()
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
