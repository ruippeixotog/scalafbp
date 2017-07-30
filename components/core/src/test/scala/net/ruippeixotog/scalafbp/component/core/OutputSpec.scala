package net.ruippeixotog.scalafbp.component.core

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class OutputSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Output

  "A Output component" should {

    "send every packet it receives as an Output message" in new ComponentInstance {
      Output.inPorts("in").send(JsNumber(2))
      this must sendOutput("2")

      Output.inPorts("in").send(JsArray(JsNumber(1)))
      this must sendOutput("[1]")
    }

    "forward every packet it receives to the out port" in new ComponentInstance {
      Output.inPorts("in").send("2".parseJson)
      Output.outPorts("out") must emit("2".parseJson)

      Output.inPorts("in").send("[1,2]".parseJson)
      Output.outPorts("out") must emit("[1,2]".parseJson)

      this must not(terminate())
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
