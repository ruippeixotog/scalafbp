package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class TakeSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Take

  "A Take component" should {

    "not output anything until the number of elements to take is known" in new ComponentInstance {
      Take.inPort.send(JsNumber(3))
      Take.outPort must receiveNothing
    }

    "start emitting the input elements immediately after n is known" in new ComponentInstance {
      Take.inPort.send(JsNumber(3))
      Take.inPort.send(JsNumber(5))
      Take.outPort must receiveNothing

      Take.nPort.send(3)
      Take.outPort must receive(JsNumber(3))
      Take.outPort must receive(JsNumber(5))
    }

    "emit at most the first n elements of the input" in new ComponentInstance {
      Take.nPort.send(2)
      Take.inPort.send(JsNumber(3))
      Take.outPort must receive(JsNumber(3))
      Take.inPort.send(JsNumber(1))
      Take.outPort must receive(JsNumber(1))
      Take.inPort.send(JsNumber(10))
      Take.outPort must receiveNothing
    }

    "terminate after n elements are sent to the output" in new ComponentInstance {
      Take.nPort.send(2)
      Take.inPort.send(JsNumber(3))
      Take.inPort.send(JsNumber(1))
      this must terminate()
    }

    "terminate immediately if no number of elements is sent" in new ComponentInstance {
      Take.nPort.close()
      this must terminate()
    }

    "terminate when all ports are closed after some messages are received" in new ComponentInstance {
      Take.nPort.send(3)
      Take.inPort.send(JsNumber(3))
      Take.nPort.close()
      Take.inPort.close()
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
