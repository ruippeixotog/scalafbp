package net.ruippeixotog.scalafbp.protocol

import akka.actor._

import net.ruippeixotog.scalafbp.protocol.message.Component.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.Graph.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.Message
import net.ruippeixotog.scalafbp.protocol.message.Network.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.Runtime.RuntimeMessage
import net.ruippeixotog.scalafbp.protocol.message.Trace.TraceMessage

class MainProtocolActor(logicActor: ActorRef) extends AbstractProtocolActor[Message] {
  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor))
  val componentProtocolActor = context.actorOf(Props(new ComponentProtocolActor))
  val graphProtocolActor = context.actorOf(Props(new GraphProtocolActor(logicActor)))
  val networkProtocolActor = context.actorOf(Props(new NetworkProtocolActor(logicActor)))
  val traceProtocolActor = context.actorOf(Props(new TraceProtocolActor))

  def receiveMessage = {
    case p: RuntimeMessage => runtimeProtocolActor.forward(p)
    case p: ComponentMessage => componentProtocolActor.forward(p)
    case p: GraphMessage => graphProtocolActor.forward(p)
    case p: NetworkMessage => networkProtocolActor.forward(p)
    case p: TraceMessage => traceProtocolActor.forward(p)
  }
}
