package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.runtime.LogicActor
import net.ruippeixotog.scalafbp.runtime.LogicActor.{ GetNetworkStatus, StartNetwork, StopNetwork }
import net.ruippeixotog.scalafbp.protocol.message.ModelConversions._

class NetworkProtocolActor(logicActor: ActorRef) extends AbstractProtocolActor[NetworkMessage] {
  var outputActor: ActorRef = context.system.deadLetters // TODO improve this hack

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def receiveMessage = {
    case payload: GetStatus =>
      val replyTo = sender()
      (logicActor ? GetNetworkStatus(payload.graph)).map {
        case st: LogicActor.Status => replyTo ! st.toStatusMessage
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Start =>
      outputActor = sender()
      val replyTo = sender()
      (logicActor ? StartNetwork(payload.graph)).map {
        case st: LogicActor.Status => replyTo ! st.toStartedMessage()
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Stop =>
      val replyTo = sender()
      (logicActor ? StopNetwork(payload.graph)).map {
        case st: LogicActor.Status => replyTo ! st.toStoppedMessage()
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Debug =>
      sender() ! payload.copy(enable = false)

    case msg: ComponentActor.Output =>
      outputActor ! Output(msg.message, Some(msg.msgType), msg.url)
  }
}
