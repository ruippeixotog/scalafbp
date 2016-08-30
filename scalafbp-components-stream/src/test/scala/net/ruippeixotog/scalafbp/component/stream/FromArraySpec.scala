package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class FromArraySpec extends ComponentSpec with AutoTerminateSpec {
  val component = FromArray

  "A FromArray component" should {

    "Emit each element of the arrays as they are received" in new ComponentInstance {
      FromArray.arrayPort.send(List(JsTrue, JsFalse))
      FromArray.outPort must receive(JsTrue)
      FromArray.outPort must receive(JsFalse)
      FromArray.outPort must receiveNothing

      FromArray.arrayPort.send(List(JsArray(JsNumber(3.0))))
      FromArray.outPort must receive(JsArray(JsNumber(3.0)))
      FromArray.outPort must receiveNothing

      FromArray.arrayPort.send(Nil)
      FromArray.outPort must receiveNothing
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
