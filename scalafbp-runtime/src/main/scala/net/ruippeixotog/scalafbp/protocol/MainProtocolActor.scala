package net.ruippeixotog.scalafbp.protocol

import akka.actor._
import com.typesafe.config.Config

import net.ruippeixotog.scalafbp.protocol.message._
import net.ruippeixotog.scalafbp.runtime.ComponentRegistry

class MainProtocolActor(
    runtimeId: String,
    secret: String,
    compRegistry: ActorRef,
    graphStore: ActorRef,
    runtimeConfig: Config) extends AbstractProtocolActor[Message] {

  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor(runtimeId)))
  val componentProtocolActor = context.actorOf(Props(new ComponentProtocolActor(compRegistry, graphStore)))
  val graphProtocolActor = context.actorOf(Props(new GraphProtocolActor(compRegistry, graphStore)))
  val networkProtocolActor = context.actorOf(Props(new NetworkProtocolActor(graphStore, runtimeConfig)))
  val traceProtocolActor = context.actorOf(Props(new TraceProtocolActor))

  def receiveMessage = {
    case p: RuntimeMessage => runtimeProtocolActor.forward(p)
    case p: ComponentMessage => componentProtocolActor.forward(p)
    case p: GraphMessage => graphProtocolActor.forward(p)
    case p: NetworkMessage => networkProtocolActor.forward(p)
    case p: TraceMessage => traceProtocolActor.forward(p)
  }
}
