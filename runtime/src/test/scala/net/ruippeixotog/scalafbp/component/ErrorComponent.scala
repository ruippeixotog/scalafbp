package net.ruippeixotog.scalafbp.component

import akka.actor.{ Actor, Props }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor.Incoming

case object ErrorComponent extends Component {
  val name = "ErrorComponent"
  val description = ""
  val icon = None

  val inPorts = List(InPort[JsValue](s"in", ""))
  val outPorts = Nil

  val instanceProps = Props(new Actor {
    def receive = {
      case Incoming(_, _) => throw new RuntimeException("process error")
    }
  })
}
