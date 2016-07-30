package net.ruippeixotog.scalafbp.runtime

import akka.actor._

import net.ruippeixotog.scalafbp.protocol._

class MainProtocolActor extends Actor {
  val logicActor = context.actorOf(Props(new LogicActor))

  val runtimeProtocolActor = context.actorOf(Props(new RuntimeProtocolActor))
  val componentProtocolActor = context.actorOf(Props(new ComponentProtocolActor))
  val graphProtocolActor = context.actorOf(Props(new GraphProtocolActor(logicActor)))
  val networkProtocolActor = context.actorOf(Props(new NetworkProtocolActor(logicActor)))

  def receive = {
    case Runtime(payload) => runtimeProtocolActor.forward(payload)
    case Component(payload) => componentProtocolActor.forward(payload)
    case Graph(payload) => graphProtocolActor.forward(payload)
    case Network(payload) => networkProtocolActor.forward(payload)
    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
