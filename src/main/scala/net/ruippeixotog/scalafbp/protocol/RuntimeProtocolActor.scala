package net.ruippeixotog.scalafbp.protocol

import akka.actor.Actor

import net.ruippeixotog.scalafbp.protocol.message.Runtime._

class RuntimeProtocolActor extends Actor {

  def receive = {
    case _: GetRuntime =>
      sender() ! Runtime(
        `type` = "fbp-scala-example",
        version = "0.4",
        capabilities = List("protocol:component", "protocol:network"),
        allCapabilities = List("protocol:component", "protocol:network"),
        id = None,
        label = None,
        graph = None)

    case packet: Packet =>
      sender() ! packet.copy(port = "out")

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
