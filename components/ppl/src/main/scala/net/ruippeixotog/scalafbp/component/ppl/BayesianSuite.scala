package net.ruippeixotog.scalafbp.component.ppl

import akka.actor.Props
import spray.json.JsValue
import spray.json.DefaultJsonProtocol._
import thinkbayes.{ Pmf, Suite }

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.PmfJsonProtocol._
import net.ruippeixotog.scalafbp.util.NashornEngine

case object BayesianSuite extends Component {
  val name = "ppl/BayesianSuite"
  val description = "Performs Bayesian inference as observations are received"
  val icon = Some("cogs")

  val priorPort = InPort[Pmf[JsValue]]("prior", "The prior distribution as a random variable")
  val lhoodPort = InPort[String]("lhood", "The likelihood function with arguments (data, hypo) returning the " +
    "probability of observing 'data' given 'hypo'")
  val obsPort = InPort[JsValue]("obs", "The stream of observations")
  val inPorts = List(priorPort, lhoodPort, obsPort)

  val posteriorPort = OutPort[Pmf[JsValue]]("posterior", "The posterior distribution as a random variable")
  val outPorts = List(posteriorPort)

  val instanceProps = Props(new ComponentActor(this) with NashornEngine {
    val obs = obsPort.stream
    val prior = priorPort.stream.head.map(_.toPmf)
    val lhood = lhoodPort.stream.head.map(JsFunction2.typed[JsValue, JsValue, Double](_, "data", "hypo"))

    prior.zipWith(lhood)(Suite(_)(_)).flatMap { suite =>
      obs.scan(suite)(_.observed(_)).map(_.pmf)
    }.pipeTo(posteriorPort)
  })
}
