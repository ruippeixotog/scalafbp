package net.ruippeixotog.scalafbp.component.math

import akka.actor.Props

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.VarDefinition
import net.ruippeixotog.scalafbp.component._

case object Add extends Component {
  val name = "math/Add"
  val description = "Adds two values"
  val icon = Some("plus")
  val isSubgraph = true

  val augendPort = InPort[Double]("augend", "The first number to add")
  val addendPort = InPort[Double]("addend", "The second number to add")
  val inPorts = List(augendPort, addendPort)

  val sumPort = OutPort[Double]("sum", "The sum of the two inputs")
  val outPorts = List(sumPort)

  val instanceProps = Props(new SimpleComponentActor(this) with VarDefinition {
    val sum = for { a1 <- augendPort.value; a2 <- addendPort.value } yield a1 + a2
    sum.pipeTo(sumPort)
  })
}
