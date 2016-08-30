package net.ruippeixotog.scalafbp.component.core

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class MakeFunctionSpec extends ComponentSpec with AutoTerminateSpec {
  val component = MakeFunction

  "A MakeFunction component" should {

    "not output anything until the function is known" in new ComponentInstance {
      MakeFunction.inPort.send(JsNumber(3.0))
      MakeFunction.outPort must receiveNothing
    }

    "correctly compile a function and run against inputs" in new ComponentInstance {
      MakeFunction.funcPort.send("return x * 2")
      MakeFunction.inPort.send(JsNumber(3.0))
      MakeFunction.outPort must receive(JsNumber(6.0))

      MakeFunction.inPort.send(JsNumber(8))
      MakeFunction.outPort must receive(JsNumber(16))
    }

    "update the mapping function if a new one is received" in new ComponentInstance {
      MakeFunction.funcPort.send("return x + 'abc'")
      MakeFunction.inPort.send(JsString("aaa"))
      MakeFunction.outPort must receive(JsString("aaaabc"))

      MakeFunction.funcPort.send("return !x")
      MakeFunction.inPort.send(JsFalse)
      MakeFunction.outPort must receive(JsTrue)
    }

    terminateItselfWhenAllInPortsAreClosed
    terminateItselfWhenAllOutPortsAreClosed
  }
}
