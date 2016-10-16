package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class FromArraySpec extends ComponentSpec with AutoTerminateSpec {
  val component = FromArray

  "A FromArray component" should {

    "Emit each element of the arrays as they are received" in new ComponentInstance {
      FromArray.arrayPort.send(List(JsTrue, JsFalse))
      FromArray.outPort must emit(JsTrue)
      FromArray.outPort must emit(JsFalse)
      FromArray.outPort must emitNothing

      FromArray.arrayPort.send(List(JsArray(JsNumber(3.0))))
      FromArray.outPort must emit(JsArray(JsNumber(3.0)))
      FromArray.outPort must emitNothing

      FromArray.arrayPort.send(Nil)
      FromArray.outPort must emitNothing
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
