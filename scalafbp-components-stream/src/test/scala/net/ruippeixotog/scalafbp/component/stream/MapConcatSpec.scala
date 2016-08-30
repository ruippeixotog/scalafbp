package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class MapConcatSpec extends ComponentSpec with AutoTerminateSpec {
  val component = MapConcat

  "A MapConcat component" should {

    "let every element pass untouched until the function is defined" in new ComponentInstance {
      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(0))
      MapConcat.inPort.send(JsNumber(3))
      MapConcat.outPort must receive(JsNumber(3))
    }

    "use the transformation function once it is defined" in new ComponentInstance {
      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(0))

      MapConcat.funcPort.send("return [x - 1, x + 1]")
      MapConcat.outPort must receiveNothing

      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(-1))
      MapConcat.outPort must receive(JsNumber(1))
      MapConcat.inPort.send(JsNumber(3))
      MapConcat.outPort must receive(JsNumber(2))
      MapConcat.outPort must receive(JsNumber(4))
    }

    "update the transformation function if it changes" in new ComponentInstance {
      MapConcat.funcPort.send("return [x * 2, x * 3, x * 4]")
      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(0))
      MapConcat.inPort.send(JsNumber(3))
      MapConcat.outPort must receive(JsNumber(6))
      MapConcat.outPort must receive(JsNumber(9))
      MapConcat.outPort must receive(JsNumber(12))

      MapConcat.funcPort.send("return [-1]")
      MapConcat.outPort must receiveNothing

      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receive(JsNumber(-1))
      MapConcat.inPort.send(JsNumber(3))
      MapConcat.outPort must receive(JsNumber(-1))

      MapConcat.funcPort.send("return []")
      MapConcat.outPort must receiveNothing

      MapConcat.inPort.send(JsNumber(0))
      MapConcat.outPort must receiveNothing
    }

    "terminate with a ProcessError if the function does not return an array" in new ComponentInstance {
      MapConcat.funcPort.send("return x")
      MapConcat.inPort.send(JsNumber(0))
      this must terminateWithProcessError()
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
