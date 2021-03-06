package net.ruippeixotog.scalafbp.component

import akka.actor.Props

case class DummyComponent[A: PortDataMarshaller](inCount: Int, outCount: Int, props: Props = Props.empty)
    extends Component {

  val name = getClass.getSimpleName
  val description = getClass.getName
  val icon = None

  val inPorts = (1 to inCount).toList.map { i => InPort[A](s"in$i", "") }
  val outPorts = (1 to outCount).toList.map { i => OutPort[A](s"out$i", "") }
  val instanceProps = props

  def withProps(newProps: Props) = DummyComponent[A](inCount, outCount, newProps)
}
