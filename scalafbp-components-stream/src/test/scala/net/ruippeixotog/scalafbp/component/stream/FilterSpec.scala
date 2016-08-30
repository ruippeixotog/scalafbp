package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class FilterSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Filter

  "A Filter component" should {

    "let every element pass until the function is defined" in new ComponentInstance {
      Filter.inPort.send(JsNumber(0))
      Filter.outPort must receive(JsNumber(0))
      Filter.inPort.send(JsNumber(3))
      Filter.outPort must receive(JsNumber(3))
    }

    "use the filtering function once it is defined" in new ComponentInstance {
      Filter.inPort.send(JsNumber(0))
      Filter.outPort must receive(JsNumber(0))

      Filter.funcPort.send("return x > 0")
      Filter.outPort must receiveNothing

      Filter.inPort.send(JsNumber(0))
      Filter.outPort must receiveNothing
      Filter.inPort.send(JsNumber(3))
      Filter.outPort must receive(JsNumber(3))
    }

    "update the filtering function if it changes" in new ComponentInstance {
      Filter.funcPort.send("return x > 0")
      Filter.inPort.send(JsNumber(3))
      Filter.outPort must receive(JsNumber(3))
      Filter.inPort.send(JsNumber(0))
      Filter.outPort must receiveNothing

      Filter.funcPort.send("return x <= 0")
      Filter.outPort must receiveNothing

      Filter.inPort.send(JsNumber(0))
      Filter.outPort must receive(JsNumber(0))
      Filter.inPort.send(JsNumber(3))
      Filter.outPort must receiveNothing
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
