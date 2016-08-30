package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class MapSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Map

  "A Map component" should {

    "let every element pass untouched until the function is defined" in new ComponentInstance {
      Map.inPort.send(JsNumber(0))
      Map.outPort must receive(JsNumber(0))
      Map.inPort.send(JsNumber(3))
      Map.outPort must receive(JsNumber(3))
    }

    "use the transformation function once it is defined" in new ComponentInstance {
      Map.inPort.send(JsNumber(0))
      Map.outPort must receive(JsNumber(0))

      Map.funcPort.send("return x * 2 + 1")
      Map.outPort must receiveNothing

      Map.inPort.send(JsNumber(0))
      Map.outPort must receive(JsNumber(1))
      Map.inPort.send(JsNumber(3))
      Map.outPort must receive(JsNumber(7))
    }

    "update the transformation function if it changes" in new ComponentInstance {
      Map.funcPort.send("return x * 2 + 1")
      Map.inPort.send(JsNumber(0))
      Map.outPort must receive(JsNumber(1))
      Map.inPort.send(JsNumber(3))
      Map.outPort must receive(JsNumber(7))

      Map.funcPort.send("return (x + 2) * 2")
      Map.outPort must receiveNothing

      Map.inPort.send(JsNumber(0))
      Map.outPort must receive(JsNumber(4))
      Map.inPort.send(JsNumber(3))
      Map.outPort must receive(JsNumber(10))
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
