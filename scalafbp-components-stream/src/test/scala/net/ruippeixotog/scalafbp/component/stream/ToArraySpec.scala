package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ToArraySpec extends ComponentSpec with AutoTerminateSpec {
  val component = ToArray

  "A ToArray component" should {

    "Accumulate packets as packets are received from the input" in new ComponentInstance {
      ToArray.inPort.send(JsTrue)
      ToArray.inPort.send(JsNumber(3.0))
      ToArray.arrayPort must receiveNothing
    }

    "Send the accumulated array immediatly after the in port closes" in new ComponentInstance {
      ToArray.inPort.send(JsTrue)
      ToArray.inPort.send(JsNumber(3.0))
      ToArray.inPort.close()
      ToArray.arrayPort must receive(List(JsTrue, JsNumber(3.0)))
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
