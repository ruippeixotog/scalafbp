package net.ruippeixotog.scalafbp.protocol

import akka.actor.Actor

import net.ruippeixotog.scalafbp.protocol.message.RuntimeMessages._
import net.ruippeixotog.scalafbp.protocol.message.{ Runtime => RuntimeProtocol }

class RuntimeProtocolActor extends Actor {
  def wrap(payload: Payload) = RuntimeProtocol(payload)

  def receive = {
    case _: GetRuntime =>
      sender() ! wrap(Runtime(
        `type` = "fbp-scala-example",
        version = "0.4",
        capabilities = List("protocol:component", "protocol:network"),
        allCapabilities = List("protocol:component", "protocol:network"),
        id = None,
        label = None,
        graph = None))

    case packet: Packet =>
      sender() ! wrap(packet.copy(port = "out"))

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
