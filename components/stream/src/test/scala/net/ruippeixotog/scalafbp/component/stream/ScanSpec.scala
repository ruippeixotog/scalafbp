package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ScanSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Scan

  "A Scan component" should {

    "not output anything until the initial value is known" in new ComponentInstance {
      Scan.funcPort.send("return acc + 1")
      Scan.inPort.send(JsNumber(3))
      Scan.outPort must emitNothing
    }

    "not output anything until the function is known" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.inPort.send(JsNumber(3))
      Scan.outPort must emitNothing
    }

    "output the initial value first when both initial parameters are known" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x")
      Scan.outPort must emit(JsNumber(0))
    }

    "output the accumulated value as inputs arrive" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x * 2")
      Scan.outPort must emit(JsNumber(0))

      Scan.inPort.send(JsNumber(3))
      Scan.outPort must emit(JsNumber(6))

      Scan.inPort.send(JsNumber(1))
      Scan.outPort must emit(JsNumber(8))

      Scan.inPort.send(JsNumber(10))
      Scan.outPort must emit(JsNumber(28))
    }

    "consider inputs arrived before the initial parameters are known" in new ComponentInstance {
      Scan.inPort.send(JsNumber(3))
      Scan.inPort.send(JsNumber(1))
      Scan.inPort.send(JsNumber(10))

      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x * 2")
      Scan.outPort must emit(JsNumber(0))
      Scan.outPort must emit(JsNumber(6))
      Scan.outPort must emit(JsNumber(8))
      Scan.outPort must emit(JsNumber(28))
    }

    "terminate with an error if no data is received on the initial value port" in new ComponentInstance {
      Scan.initialPort.close()
      this must terminateWithError()
    }

    "terminate with an error if no data is received on the function port" in new ComponentInstance {
      Scan.funcPort.close()
      this must terminateWithError()
    }

    "terminate when all ports are closed after some messages are received" in new ComponentInstance {
      Scan.initialPort.send(JsNumber(0))
      Scan.funcPort.send("return acc + x")
      Scan.inPort.send(JsNumber(3))
      Scan.initialPort.close()
      Scan.funcPort.close()
      Scan.inPort.close()
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
