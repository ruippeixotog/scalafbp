package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.protocol.message.TraceMessage

class TraceProtocolActor extends AbstractProtocolActor[TraceMessage] {
  def receiveMessage = PartialFunction.empty
}
