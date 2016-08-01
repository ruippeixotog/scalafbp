package net.ruippeixotog.scalafbp.protocol

import akka.actor._

import net.ruippeixotog.scalafbp.protocol.message.Component.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.Graph.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.Network.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.Runtime.RuntimeMessage
import net.ruippeixotog.scalafbp.runtime.LogicActor

class MainProtocolActor(logicActor: ActorRef) extends Actor {
  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor))
  val componentProtocolActor = context.actorOf(Props(new ComponentProtocolActor))
  val graphProtocolActor = context.actorOf(Props(new GraphProtocolActor(logicActor)))
  val networkProtocolActor = context.actorOf(Props(new NetworkProtocolActor(logicActor)))

  def receive = {
    case p: RuntimeMessage => runtimeProtocolActor.forward(p)
    case p: ComponentMessage => componentProtocolActor.forward(p)
    case p: GraphMessage => graphProtocolActor.forward(p)
    case p: NetworkMessage => networkProtocolActor.forward(p)
    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
