package net.ruippeixotog.scalafbp.component.stream

import spray.json._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class ConcatSpec extends ComponentSpec with AutoTerminateSpec {
  val component = Concat

  "A Concat component" should {

    "not output anything until the first stream sends packets" in new ComponentInstance {
      Concat.in2Port.send(JsTrue)
      Concat.in2Port.send(JsNumber(3.0))
      Concat.outPort must not(receiveLike { case _ => ok })
      Concat.in2Port.close()
      Concat.outPort must not(receiveLike { case _ => ok })
    }

    "Send immediatly the packets from the first stream" in new ComponentInstance {
      Concat.in1Port.send(JsTrue)
      Concat.in2Port.send(JsNumber(3.0))
      Concat.outPort must receive(JsTrue)
      Concat.outPort must not(receiveLike { case _ => ok })
    }

    "Send the packets from the second stream after the first one is closed" in new ComponentInstance {
      Concat.in1Port.send(JsTrue)
      Concat.in2Port.send(JsNumber(3))
      Concat.in2Port.send(JsNumber(6))
      Concat.outPort must receive(JsTrue)
      Concat.outPort must not(receiveLike { case _ => ok })

      Concat.in1Port.close()
      Concat.outPort must receive(JsNumber(3))
      Concat.outPort must receive(JsNumber(6))
    }

    "Terminate itself even when the second stream was closed before the first" in new ComponentInstance {
      Concat.in1Port.send(JsTrue)
      Concat.in2Port.send(JsNumber(3))
      Concat.in2Port.close()
      Concat.outPort must receive(JsTrue)
      Concat.outPort must not(receiveLike { case _ => ok })

      Concat.in1Port.close()
      Concat.outPort must receive(JsNumber(3))
      this must terminate()
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
