package net.ruippeixotog.scalafbp.http

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.model.ws.{ Message => WsMessage, TextMessage => WsTextMessage }
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import spray.json._

import net.ruippeixotog.scalafbp.protocol.message.Message
import net.ruippeixotog.scalafbp.ws.SubscriptionManagerActor._
import net.ruippeixotog.scalafbp.ws.{ SubscriptionManagerActor, WsUtils }

trait WsRuntimeHttpService extends WsUtils with SLF4JLogging {

  implicit def system: ActorSystem
  implicit def materializer: Materializer
  implicit def ec: ExecutionContext

  // actor that receives incoming messages (as `Message` objects) and replies with `Message` objects to clients
  def protocolActor: ActorRef

  // actor dealing with the subscription of multiple WebSocket clients
  private[this] lazy val wsManagerActor = system.actorOf(Props(new SubscriptionManagerActor(protocolActor)))

  // reads a WebSocket message and parses it into a `Message`. The returned list is either empty or have one element;
  // it's not encoded as an `Option` as `mapConcat` expects an `immutable.Iterable`.
  private[this] def readWsMessage(wsMsg: WsMessage): List[Message] = wsMsg match {
    case WsTextMessage.Strict(text) =>
      Try(text.parseJson.convertTo[Message]) match {
        case Success(msg) => List(msg)
        case Failure(_) =>
          log.warn(s"Received illegal FBP message: $text")
          Nil
      }
    case _ => Nil
  }

  // converts a `Message` into a WebScoket message simply by serializing it to JSON
  private[this] def writeWsMessage(msg: Message): WsMessage = WsTextMessage(msg.toJson.compactPrint)

  // creates a `Flow` of WebSocket messages encompassing incoming and outgoing messages to/from a client
  private[this] def fbpRuntimeFlow(id: String): Flow[WsMessage, WsMessage, Any] = {
    Flow[WsMessage]
      .mapConcat(readWsMessage)
      .via(subscriptionFlow[Message, Message](id, wsManagerActor))
      .map(writeWsMessage)
  }

  // format: OFF
  lazy val wsRuntimeRoutes =
    pathEndOrSingleSlash {
      provide(UUID.randomUUID().toString.take(8)) { clientId =>
        handleWebSocketMessagesForProtocol({
          logWsMessages(clientId) {
            fbpRuntimeFlow(clientId)
          }
        }, "noflo")
      }
    }
  // format: ON
}
