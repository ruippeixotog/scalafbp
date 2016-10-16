package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class DropSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Drop

  "A Drop component" should {

    "not output anything until the number of elements to drop is known" in new ComponentInstance {
      Drop.inPort.send(JsNumber(3))
      Drop.outPort must emitNothing
    }

    "emit only the input elements after the first n ones" in new ComponentInstance {
      Drop.nPort.send(2)
      Drop.inPort.send(JsNumber(3))
      Drop.outPort must emitNothing
      Drop.inPort.send(JsNumber(1))
      Drop.outPort must emitNothing
      Drop.inPort.send(JsNumber(10))
      Drop.outPort must emit(JsNumber(10))
    }

    "drop the correct elements even if n is only known afterwards" in new ComponentInstance {
      Drop.inPort.send(JsNumber(3))
      Drop.inPort.send(JsNumber(5))
      Drop.outPort must emitNothing

      Drop.nPort.send(1)
      Drop.outPort must emit(JsNumber(5))
      Drop.outPort must emitNothing
    }

    "terminate with a ProcessError if no number of elements is sent" in new ComponentInstance {
      Drop.nPort.close()
      this must terminateWithProcessError()
    }

    "terminate when all ports are closed after some messages are received" in new ComponentInstance {
      Drop.nPort.send(3)
      Drop.inPort.send(JsNumber(3))
      Drop.nPort.close()
      Drop.inPort.close()
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
