package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ScanSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Scan

  "A Scan component" should {

    "not output anything until the initial value is known" in new ComponentInstance {
      Scan.funcPort.send("return acc + 1")
      Scan.inPort.send(JsNumber(3))
      Scan.outPort must not(receiveLike { case _ => ok })
    }

    "not output anything until the function is known" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.inPort.send(JsNumber(3))
      Scan.outPort must not(receiveLike { case _ => ok })
    }

    "output the initial value first when both all inputs are known" in new ComponentInstance {
      Scan.inPort.send(JsNumber(3))
      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x")
      Scan.outPort must receive(JsNumber(0))
      Scan.outPort must receive(JsNumber(3))
    }

    "output the accumulated value as inputs arrive" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x * 2")

      Scan.inPort.send(JsNumber(3))
      Scan.outPort must receive(JsNumber(0))
      Scan.outPort must receive(JsNumber(6))

      Scan.inPort.send(JsNumber(1))
      Scan.outPort must receive(JsNumber(8))

      Scan.inPort.send(JsNumber(10))
      Scan.outPort must receive(JsNumber(28))
    }

    "terminate immediately if the initial value port does not send any data" in new ComponentInstance {
      Scan.initialPort.close()
      this must terminate()
    }

    "terminate immediately if the function port does not send any data" in new ComponentInstance {
      Scan.funcPort.close()
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
