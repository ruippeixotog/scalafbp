package net.ruippeixotog.scalafbp.ws

import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.stream.scaladsl.Flow

trait WsUtils extends SLF4JLogging {

  private[this] def printMessage(msg: Message): String = msg match {
    case TextMessage.Strict(text) => text
    case _: TextMessage.Streamed => "(streamed text message)"
    case _: BinaryMessage => "(binary message)"
  }

  def logWsMessages(id: String)(handler: Flow[Message, Message, Any]) = {
    Flow[Message]
      .map { msg => log.debug(s"[$id] IN : ${printMessage(msg)}"); msg }
      .via(handler)
      .map { msg => log.debug(s"[$id] OUT: ${printMessage(msg)}"); msg }
  }
}
