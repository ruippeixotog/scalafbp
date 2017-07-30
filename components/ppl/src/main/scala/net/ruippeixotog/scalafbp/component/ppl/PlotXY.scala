package net.ruippeixotog.scalafbp.component.ppl

import akka.actor.Props
import spray.json.JsNumber
import thinkbayes.extensions.Plotting._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

case object PlotXY extends Component {
  val name = "ppl/PlotXY"
  val description = "Plots in a line chart the PMF of a numeric random variable"
  val icon = Some("line-chart")

  val varPort = InPort[PVar[JsNumber]]("var", "The random variable to plot. Must be numeric")
  val titlePort = InPort[String]("title", "The chart title")
  val inPorts = List(varPort, titlePort)

  val varOutPort = OutPort[PVar[JsNumber]]("var", "The input random variable")
  val outPorts = List(varOutPort)

  val instanceProps = Props(new ComponentActor(this) {
    override val terminationPolicy = Nil

    val chart = emptyPlotXY()
    val controls = chart.showControls

    controls.show()
    controls.onHide(Option(context).foreach(_.stop(self)))
    override def postStop() = controls.dispose()

    titlePort.stream.foreach(chart.title = _)

    varPort.stream
      .doOnEach(_.toPmf.mapKeys(_.value).plotXYOn(chart, "var"))
      .pipeTo(varOutPort)
  })
}
