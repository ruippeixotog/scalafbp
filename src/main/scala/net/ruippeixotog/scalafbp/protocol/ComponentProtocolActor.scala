package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.{ List => ListComponents, _ }
import net.ruippeixotog.scalafbp.protocol.message.ModelConversions._

class ComponentProtocolActor(compRegistry: ComponentRegistry) extends AbstractProtocolActor[ComponentMessage] {

  def receiveMessage = {
    case _: ListComponents =>
      compRegistry.iterator.foreach { comp => sender() ! comp.toMessage }
      sender() ! ComponentsReady(compRegistry.size)
  }
}
