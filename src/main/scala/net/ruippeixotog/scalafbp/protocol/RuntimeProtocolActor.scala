package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.protocol.message.RuntimeMessage
import net.ruippeixotog.scalafbp.protocol.message.RuntimeMessages._

class RuntimeProtocolActor(runtimeId: String) extends AbstractProtocolActor[RuntimeMessage] {

  val capabilities = List(
    "protocol:graph",
    "protocol:component",
    "protocol:network",
    "protocol:runtime")

  def receiveMessage = {
    case _: GetRuntime =>
      sender() ! Runtime(
        `type` = "scalafbp",
        version = "0.5",
        capabilities = capabilities,
        allCapabilities = capabilities,
        id = Some(runtimeId),
        label = Some("Scala FBP Runtime"),
        graph = None)
  }
}
