package net.ruippeixotog.scalafbp.component.math

import akka.actor.Props

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.VarDefinition
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort, SimpleComponentActor }
import net.ruippeixotog.scalafbp.util.Var

case object Add extends Component {
  val name = "math/Add"
  val description = "Adds two values"
  val icon = Some("plus")
  val isSubgraph = true

  val inPorts = List(
    InPort[Double]("augend", "The first number to add"),
    InPort[Double]("addend", "The second number to add"))

  val outPorts = List(
    OutPort[Double]("sum", "The sum of the two inputs"))

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition {
    def mapInputs(in: List[Var[Any]]) = List(
      for { a1 <- in(0); a2 <- in(1) } yield a1.asInstanceOf[Double] + a2.asInstanceOf[Double])
  })
}
