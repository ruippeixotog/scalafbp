package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.protocol.message.Runtime._

class RuntimeProtocolActor(runtimeId: String) extends AbstractProtocolActor[RuntimeMessage] {

  def receiveMessage = {
    case _: GetRuntime =>
      sender() ! Runtime(
        `type` = "scalafbp",
        version = "0.5",
        capabilities = List("protocol:component", "protocol:network"),
        allCapabilities = List("protocol:component", "protocol:network"),
        id = Some(runtimeId),
        label = Some("Scala FBP Runtime"),
        graph = None)
  }
}
