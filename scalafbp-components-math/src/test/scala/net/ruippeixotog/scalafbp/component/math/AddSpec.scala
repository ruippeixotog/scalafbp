package net.ruippeixotog.scalafbp.component.math

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class AddSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Add

  "An Add component" should {

    "not output anything until the augend is known" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.outPorts("sum") must receiveNothing
    }

    "not output anything until the addend is known" in new ComponentInstance {
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must receiveNothing
    }

    "output the sum of the two inputs after both are known" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must receive(5.0)
    }

    "update the sum as the inputs change" in new ComponentInstance {
      Add.inPorts("addend").send(3.0)
      Add.inPorts("augend").send(2.0)
      Add.outPorts("sum") must receive(5.0)

      Add.inPorts("augend").send(5.5)
      Add.outPorts("sum") must receive(8.5)

      Add.inPorts("addend").send(1.5)
      Add.outPorts("sum") must receive(7.0)
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
