package net.ruippeixotog.scalafbp.protocol

import akka.actor._

import net.ruippeixotog.scalafbp.protocol.message._

class MainProtocolActor(runtimeId: String, secret: String, logicActor: ActorRef)
    extends AbstractProtocolActor[Message] {

  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor(runtimeId)))
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
