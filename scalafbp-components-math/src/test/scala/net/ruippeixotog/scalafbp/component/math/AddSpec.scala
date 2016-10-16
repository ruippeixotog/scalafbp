package net.ruippeixotog.scalafbp.component.math

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class AddSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Add

  "An Add component" should {

    "not output anything until the augend is known" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.outPorts("sum") must emitNothing
    }

    "not output anything until the addend is known" in new ComponentInstance {
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must emitNothing
    }

    "output the sum of the two inputs after both are known" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must emit(5.0)
    }

    "update the sum as the inputs change" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must emit(5.0)

      Add.inPorts("augend").send(5.5)
      Add.outPorts("sum") must emit(8.5)

      Add.inPorts("addend").send(1.5)
      Add.outPorts("sum") must emit(7.0)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
