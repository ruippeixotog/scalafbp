package net.ruippeixotog.scalafbp.component.stream

import akka.actor.{ Actor, Props, Stash }
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case object Concat extends Component {
  val name = "stream/Concat"
  val description = "Sends all packets of a stream followed by all elements of another"
  val icon = Some("compress")
  val isSubgraph = true

  val in1Port = InPort[JsValue]("in1", "The first stream")
  val in2Port = InPort[JsValue]("in2", "The second stream")
  val inPorts = List(in1Port, in2Port)

  val outPort = OutPort[JsValue]("out", "The concatenated stream")
  val outPorts = List(outPort)

  val instanceProps = Props(new Actor with Stash {

    def sendingFirst: Receive = {
      case Incoming("in1", data) => sender() ! Outgoing("out", data)
      case Incoming("in2", data) => stash()
      case InPortDisconnected("in1") =>
        unstashAll()
        context.become(sendingSecond)
      case InPortDisconnected("in2") => stash()
    }

    def sendingSecond: Receive = {
      case Incoming("in2", data) => sender() ! Outgoing("out", data)
      case InPortDisconnected("in2") => context.stop(self)
    }

    def receive = sendingFirst
  })
}
