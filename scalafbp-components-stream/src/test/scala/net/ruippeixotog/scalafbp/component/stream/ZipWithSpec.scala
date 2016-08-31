package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ZipWithSpec extends ComponentSpec with AutoTerminateSpec {
  val component = ZipWith

  "A ZipWith component" should {

    "not output anything until the first stream sends elements" in new ComponentInstance {
      ZipWith.selectorPort.send("return x1")
      ZipWith.in2Port.send(JsTrue)
      ZipWith.in2Port.send(JsNumber(3.0))
      ZipWith.outPort must receiveNothing
      ZipWith.in2Port.close()
      ZipWith.outPort must receiveNothing
    }

    "not output anything until the second stream sends elements" in new ComponentInstance {
      ZipWith.selectorPort.send("return x1")
      ZipWith.in1Port.send(JsTrue)
      ZipWith.in1Port.send(JsNumber(3.0))
      ZipWith.outPort must receiveNothing
      ZipWith.in1Port.close()
      ZipWith.outPort must receiveNothing
    }

    "not output anything until the selector is known" in new ComponentInstance {
      ZipWith.in1Port.send(JsTrue)
      ZipWith.in2Port.send(JsNumber(3))
      ZipWith.outPort must receiveNothing
    }

    "output the result of the selector function as pairs of input elements are available" in new ComponentInstance {
      ZipWith.selectorPort.send("""return { "a": x1, "b": x2 }""")
      ZipWith.in1Port.send(JsTrue)
      ZipWith.in2Port.send(JsNumber(3))
      ZipWith.outPort must receive(JsObject("a" -> JsTrue, "b" -> JsNumber(3)))

      ZipWith.in1Port.send(JsNumber(6))
      ZipWith.in1Port.send(JsNull)
      ZipWith.outPort must receiveNothing

      ZipWith.in2Port.send(JsNumber(5))
      ZipWith.outPort must receive(JsObject("a" -> JsNumber(6), "b" -> JsNumber(5)))

      ZipWith.in2Port.send(JsFalse)
      ZipWith.outPort must receive(JsObject("a" -> JsNull, "b" -> JsFalse))
    }

    "terminate with a ProcessError if no data is received on the selector port" in new ComponentInstance {
      ZipWith.selectorPort.close()
      this must terminateWithProcessError()
    }

    "terminate itself when the first input stream is closed" in new ComponentInstance {
      ZipWith.selectorPort.send("return x1")
      ZipWith.in2Port.send(JsNumber(3))
      ZipWith.in1Port.close()
      this must terminate()
    }

    "terminate itself when the second input stream is closed" in new ComponentInstance {
      ZipWith.selectorPort.send("return x1")
      ZipWith.in1Port.send(JsNumber(3))
      ZipWith.in2Port.close()
      this must terminate()
    }

    terminateItselfWhenAllOutPortsAreClosed
  }
}
