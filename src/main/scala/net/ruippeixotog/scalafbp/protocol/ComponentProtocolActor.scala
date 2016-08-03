package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.{ List => ListComponents, _ }
import net.ruippeixotog.scalafbp.protocol.message.ModelConversions._

class ComponentProtocolActor extends AbstractProtocolActor[ComponentMessage] {

  def receiveMessage = {
    case _: ListComponents =>
      ComponentRegistry.registry.values.foreach { comp => sender() ! comp.toMessage }
      sender() ! ComponentsReady(ComponentRegistry.registry.size)
  }
}
