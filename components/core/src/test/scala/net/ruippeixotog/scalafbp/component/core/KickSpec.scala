package net.ruippeixotog.scalafbp.component.core

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class KickSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Kick

  "A Repeat component" should {

    "not kick any packet if no input is received" in new ComponentInstance {
      Kick.kickPort.send(())
      Kick.outPort must emitNothing
      this must not(terminate())
    }

    "send a packet set in input everytime a kick signal is received" in new ComponentInstance {
      Kick.inPort.send(JsNumber(3))
      Kick.kickPort.send(())
      Kick.outPort must emit(JsNumber(3))
      Kick.kickPort.send(())
      Kick.outPort must emit(JsNumber(3))
    }

    "send always the most recent packet from the input" in new ComponentInstance {
      Kick.inPort.send(JsNumber(3))
      Kick.kickPort.send(())
      Kick.outPort must emit(JsNumber(3))

      Kick.inPort.send(JsNumber(6))
      Kick.kickPort.send(())
      Kick.outPort must emit(JsNumber(6))
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
