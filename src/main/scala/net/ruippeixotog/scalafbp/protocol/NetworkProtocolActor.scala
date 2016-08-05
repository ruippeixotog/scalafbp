package net.ruippeixotog.scalafbp.protocol

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.LogicActor.{ GetNetworkStatus, StartNetwork, StopNetwork }
import net.ruippeixotog.scalafbp.runtime.{ LogicActor, NetworkBroker, NetworkController }

class NetworkProtocolActor(logicActor: ActorRef) extends AbstractProtocolActor[NetworkMessage] {
  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  private[this] class OutputProxyActor(val inner: ActorRef) extends Actor {
    def receive = {
      case output: ComponentActor.Output => inner ! output.toMessage
      case error: LogicActor.Error => inner ! error.toMessage
      case finished: NetworkController.Finished => inner ! finished.toMessage
      case activity: NetworkBroker.Activity => inner ! activity.toMessage
      case msg => log.warn(s"Cannot proxy unexpected message $msg to client")
    }
  }

  private[this] val proxyActorCache = mutable.Map[ActorRef, ActorRef]() // TODO change this to a proper cache

  def receiveMessage = {
    case payload: GetStatus =>
      val replyTo = sender()
      (logicActor ? GetNetworkStatus(payload.graph)).map {
        case st: NetworkController.Status => replyTo ! st.toStatusMessage
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Start =>
      val replyTo = sender()
      val outputActor = proxyActorCache.getOrElseUpdate(
        replyTo,
        context.actorOf(Props(new OutputProxyActor(replyTo))))

      (logicActor ? StartNetwork(payload.graph, outputActor)).map {
        case st: NetworkController.Status => replyTo ! st.toStartedMessage()
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Stop =>
      val replyTo = sender()
      (logicActor ? StopNetwork(payload.graph)).map {
        case st: NetworkController.Status => replyTo ! st.toStoppedMessage()
        case LogicActor.Error(msg) => replyTo ! Error(msg)
      }

    case payload: Debug =>
      sender() ! payload.copy(enable = false)
  }
}
