package net.ruippeixotog.scalafbp

import java.util.UUID

import akka.actor.{ ActorSystem, Props }
import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ Message => WsMessage, TextMessage => WsTextMessage }
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import spray.json._

import net.ruippeixotog.scalafbp.protocol.message.Message
import net.ruippeixotog.scalafbp.protocol.message.Message._
import net.ruippeixotog.scalafbp.protocol.registry.RegistryClient
import net.ruippeixotog.scalafbp.protocol.{ MainProtocolActor, registry }
import net.ruippeixotog.scalafbp.runtime.LogicActor
import net.ruippeixotog.scalafbp.ws.SubscriptionManagerActor._
import net.ruippeixotog.scalafbp.ws.{ SubscriptionManagerActor, WsUtils }

object WebServer extends App with WsUtils with SLF4JLogging {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // the actor that serves as the central store for the state about graphs and handles the execution of networks
  val logicActor = system.actorOf(Props(new LogicActor))

  // actor that receives incoming messages (as `Message` objects) and translates them into appropriate actions for
  // `LogicActor`
  val protocolActor = system.actorOf(Props(new MainProtocolActor(logicActor)))

  // actor dealing with the subscription of multiple WebSocket clients
  val wsManagerActor = system.actorOf(Props(new SubscriptionManagerActor(protocolActor)))

  // creates a `Flow` of WebScoket messages encompassing incoming and outgoing messages to/from a client
  def fbpRuntimeFlow(id: String): Flow[WsMessage, WsMessage, Any] = {
    Flow[WsMessage]
      .collect { case WsTextMessage.Strict(text) => text.parseJson.convertTo[Message] }
      .via(subscriptionFlow[Message, Message](id, wsManagerActor))
      .map { msg => WsTextMessage(msg.toJson.compactPrint) }
  }

  val runtimeId = "28e174b3-8363-4d98-bdff-5b6862253f32"

  RegistryClient.register(registry.Runtime(
    id = runtimeId,
    `type` = "scalafbp",
    protocol = "websocket",
    address = "ws://localhost:8080/ws",
    label = "Scala FBP Runtime",
    port = 8080,
    user = "0a5fef0b-8600-4a1d-8fd4-6fa80b408fed",
    secret = ""))

  // format: OFF
  val route =
    path("ws") {
      provide(UUID.randomUUID().toString.take(8)) { clientId =>
        handleWebSocketMessages {
          logWsMessages(clientId) {
            fbpRuntimeFlow(clientId)
          }
        }
      }
    }
  // format: ON

  Http().bindAndHandle(route, "localhost", 8080).foreach { binding =>
    log.info(s"Bound to ${binding.localAddress}")
  }
}
