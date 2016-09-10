package net.ruippeixotog.scalafbp.component

import akka.actor.Props

case class DummyComponent(inCount: Int, outCount: Int) extends Component {
  val name = "DummyComponent"
  val description = ""
  val icon = None
  val isSubgraph = true

  val inPorts = (1 to inCount).toList.map { i => InPort[String](s"in$i", "") }
  val outPorts = (1 to outCount).toList.map { i => OutPort[String](s"out$i", "") }

  val instanceProps = Props.empty
}
