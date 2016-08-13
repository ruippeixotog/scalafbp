package net.ruippeixotog.scalafbp.component.core

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class DropSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Drop

  "A Drop component" should {

    "drop every packet sent to it" in new ComponentInstance {
      Drop.inPorts("in").send("2".parseJson)
      Drop.inPorts("in").send("null".parseJson)
      Drop.inPorts("in").send("""{ "a": 2.4 }""".parseJson)

      this must not(terminate())
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
