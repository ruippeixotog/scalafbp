package net.ruippeixotog.scalafbp.protocol

import akka.actor._

import net.ruippeixotog.scalafbp.protocol.message._
import net.ruippeixotog.scalafbp.runtime.LogicActor

class MainProtocolActor extends Actor {
  val logicActor = context.actorOf(Props(new LogicActor))

  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor))
  val componentProtocolActor = context.actorOf(Props(new ComponentProtocolActor))
  val graphProtocolActor = context.actorOf(Props(new GraphProtocolActor(logicActor)))
  val networkProtocolActor = context.actorOf(Props(new NetworkProtocolActor(logicActor)))

  def receive = {
    case p: RuntimeMessages.Payload => runtimeProtocolActor.forward(p)
    case p: ComponentMessages.Payload => componentProtocolActor.forward(p)
    case p: GraphMessages.Payload => graphProtocolActor.forward(p)
    case p: NetworkMessages.Payload => networkProtocolActor.forward(p)
    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
