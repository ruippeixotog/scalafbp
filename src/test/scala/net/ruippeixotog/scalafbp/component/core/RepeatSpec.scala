package net.ruippeixotog.scalafbp.component.core

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class RepeatSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Repeat

  "A Repeat component" should {

    "forward every packet it receives to the out port" in new ComponentInstance {
      Repeat.inPorts("in").send("2".parseJson)
      Repeat.outPorts("out") must receive("2".parseJson)

      Repeat.inPorts("in").send("[1,2]".parseJson)
      Repeat.outPorts("out") must receive("[1,2]".parseJson)

      this must not(terminate())
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
