package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ZipSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Zip

  "A Zip component" should {

    "not output anything until the first stream sends elements" in new ComponentInstance {
      Zip.in2Port.send(JsTrue)
      Zip.in2Port.send(JsNumber(3.0))
      Zip.outPort must receiveNothing
      Zip.in2Port.close()
      Zip.outPort must receiveNothing
    }

    "not output anything until the second stream sends elements" in new ComponentInstance {
      Zip.in1Port.send(JsTrue)
      Zip.in1Port.send(JsNumber(3.0))
      Zip.outPort must receiveNothing
      Zip.in1Port.close()
      Zip.outPort must receiveNothing
    }

    "output pairs of elements, one of each input stream, as they are available" in new ComponentInstance {
      Zip.in1Port.send(JsTrue)
      Zip.in2Port.send(JsNumber(3))
      Zip.outPort must receive((JsTrue, JsNumber(3)))

      Zip.in1Port.send(JsNumber(6))
      Zip.in1Port.send(JsNull)
      Zip.outPort must receiveNothing

      Zip.in2Port.send(JsNumber(5))
      Zip.outPort must receive((JsNumber(6), JsNumber(5)))

      Zip.in2Port.send(JsFalse)
      Zip.outPort must receive((JsNull, JsFalse))
    }

    "terminate itself when the first input stream is closed" in new ComponentInstance {
      Zip.in2Port.send(JsNumber(3))
      Zip.in1Port.close()
      this must terminate()
    }

    "terminate itself when the second input stream is closed" in new ComponentInstance {
      Zip.in1Port.send(JsNumber(3))
      Zip.in2Port.close()
      this must terminate()
    }

    terminateItselfWhenAllOutPortsAreClosed
  }
}
