package net.ruippeixotog.scalafbp.component.ppl

import akka.actor.Props
import spray.json.JsValue
import thinkbayes.extensions.Plotting._

import net.ruippeixotog.scalafbp.component._
import net.ruippeixotog.scalafbp.thinkbayes.Implicits._
import net.ruippeixotog.scalafbp.thinkbayes.PVar

case object PlotBar extends Component {
  val name = "ppl/PlotBar"
  val description = "Plots in a bar chart the PMF of a random variable"
  val icon = Some("bar-chart")

  val varPort = InPort[PVar[JsValue]]("var", "The random variable to plot")
  val titlePort = InPort[String]("title", "The chart title")
  val inPorts = List(varPort, titlePort)

  val varOutPort = OutPort[PVar[JsValue]]("var", "The input random variable")
  val outPorts = List(varOutPort)

  val instanceProps = Props(new ComponentActor(this) {
    override val terminationPolicy = Nil

    val chart = emptyPlotBar()
    val controls = chart.showControls

    controls.show()
    controls.onHide(Option(context).foreach(_.stop(self)))
    override def postStop() = controls.dispose()

    titlePort.stream.foreach(chart.title = _)

    varPort.stream
      .doOnEach(_.toPmf.plotBarOn(chart, "var"))
      .pipeTo(varOutPort)
  })
}
