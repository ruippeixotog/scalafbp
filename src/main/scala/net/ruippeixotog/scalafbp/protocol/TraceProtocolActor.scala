package net.ruippeixotog.scalafbp.protocol

import net.ruippeixotog.scalafbp.protocol.message.Trace.TraceMessage

class TraceProtocolActor extends AbstractProtocolActor[TraceMessage] {
  def receiveMessage = PartialFunction.empty
}
