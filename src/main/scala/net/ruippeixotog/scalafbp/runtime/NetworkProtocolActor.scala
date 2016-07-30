package net.ruippeixotog.scalafbp.runtime

import akka.pattern.ask
import akka.actor.{ Actor, ActorRef }
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.protocol.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.{ Network => NetworkProtocol }
import net.ruippeixotog.scalafbp.runtime.LogicActor.{ GetNetworkStatus, Ok, StartNetwork, StopNetwork }

import scala.concurrent.duration._

class NetworkProtocolActor(logicActor: ActorRef) extends Actor {
  var outputActor: ActorRef = context.system.deadLetters // TODO improve this hack

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def convertStatus(st: LogicActor.Status) = Status(st.graph, st.running, st.started, st.uptime, None)

  def wrap(payload: Payload) = NetworkProtocol(payload)

  def receive = {
    case payload: GetStatus =>
      val replyTo = sender()
      (logicActor ? GetNetworkStatus(payload.graph)).map {
        case st: LogicActor.Status =>
          replyTo ! wrap(Status(payload.graph, st.running, st.started, st.uptime, None))

        case LogicActor.Error(msg) => replyTo ! wrap(Error(msg))
      }

    case payload: Start =>
      outputActor = sender()
      val replyTo = sender()
      (logicActor ? StartNetwork(payload.graph)).map {
        case st: LogicActor.Status =>
          replyTo ! wrap(Started(payload.graph, System.currentTimeMillis(), st.running, st.started, st.uptime))

        case LogicActor.Error(msg) => replyTo ! wrap(Error(msg))
      }

    case payload: Stop =>
      val replyTo = sender()
      (logicActor ? StopNetwork(payload.graph)).map {
        case st: LogicActor.Status =>
          replyTo ! wrap(Stopped(payload.graph, System.currentTimeMillis(), st.running, st.started, st.uptime))

        case LogicActor.Error(msg) => replyTo ! wrap(Error(msg))
      }

    case payload: Debug =>
      sender() ! wrap(payload.copy(enable = false))

    case msg: ComponentActor.Output =>
      outputActor ! wrap(Output(msg.message, Some(msg.msgType), msg.url))

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
