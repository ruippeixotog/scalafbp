package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class RepeatDelayedSpec extends ComponentSpec with AutoTerminateSpec {
  val component = RepeatDelayed

  "A RepeatDelayed component" should {

    "not repeat anything until the delay is known" in new ComponentInstance {
      RepeatDelayed.inPort.send(JsNumber(3))
      RepeatDelayed.outPort must receiveNothing
    }

    "forward every packet it receives to the out port with a delay" in new ComponentInstance {
      RepeatDelayed.delayPort.send(500)

      RepeatDelayed.inPort.send(JsNumber(2))
      within(250.millis, 750.millis) {
        RepeatDelayed.outPort must receive(JsNumber(2))
      }

      RepeatDelayed.inPort.send(JsNumber(3))
      RepeatDelayed.inPort.send(JsString("Hello"))
      within(250.millis, 750.millis) {
        RepeatDelayed.outPort must receiveAllOf(JsNumber(3), JsString("Hello"))
      }
    }

    "always use the latest delay value for delaying input packets" in new ComponentInstance {
      RepeatDelayed.delayPort.send(1500)
      RepeatDelayed.inPort.send(JsNumber(1))
      RepeatDelayed.delayPort.send(500)
      RepeatDelayed.inPort.send(JsNumber(2))
      RepeatDelayed.delayPort.send(1000)
      RepeatDelayed.inPort.send(JsNumber(3))

      RepeatDelayed.outPort must receive(JsNumber(2))
      RepeatDelayed.outPort must receive(JsNumber(3))
      RepeatDelayed.outPort must receive(JsNumber(1))
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
