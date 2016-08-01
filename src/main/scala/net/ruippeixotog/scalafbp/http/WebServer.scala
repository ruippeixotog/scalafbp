package net.ruippeixotog.scalafbp.http

import java.util.UUID

import scala.util.{ Failure, Success, Try }

import akka.actor.{ ActorSystem, Props }
import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ Message => WsMessage, TextMessage => WsTextMessage }
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Flow
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import com.typesafe.config.ConfigFactory
import spray.json._

import net.ruippeixotog.scalafbp.protocol.MainProtocolActor
import net.ruippeixotog.scalafbp.protocol.message.Message
import net.ruippeixotog.scalafbp.protocol.message.Message._
import net.ruippeixotog.scalafbp.runtime.LogicActor
import net.ruippeixotog.scalafbp.ws.SubscriptionManagerActor._
import net.ruippeixotog.scalafbp.ws.{ SubscriptionManagerActor, WsUtils }

object WebServer extends App with RegistrationHttpService with WsUtils with SLF4JLogging {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  val decider: Supervision.Decider = { e =>
    log.error("Unhandled exception in stream", e)
    Supervision.Stop
  }

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  val config = ConfigFactory.load.getConfig("scalafbp")
  val registryConfig = config.getConfig("registry")

  val runtimeId = config.getString("runtime-id")
  val secret = config.getString("secret")

  val host = config.getString("host")
  val port = config.getInt("port")

  // the actor that serves as the central store for the state about graphs and handles the execution of networks
  val logicActor = system.actorOf(Props(new LogicActor))

  // actor that receives incoming messages (as `Message` objects) and translates them into appropriate actions for
  // `LogicActor`
  val protocolActor = system.actorOf(Props(new MainProtocolActor(logicActor)))

  // actor dealing with the subscription of multiple WebSocket clients
  val wsManagerActor = system.actorOf(Props(new SubscriptionManagerActor(protocolActor)))

  def readWsMessage(wsMsg: WsMessage): List[Message] = wsMsg match {
    case WsTextMessage.Strict(text) =>
      Try(text.parseJson.convertTo[Message]) match {
        case Success(msg) => List(msg)
        case Failure(_) =>
          log.warn(s"Received illegal FBP message: $text")
          Nil
      }
    case _ => Nil
  }

  def writeWsMessage(msg: Message): WsMessage = WsTextMessage(msg.toJson.compactPrint)

  // creates a `Flow` of WebScoket messages encompassing incoming and outgoing messages to/from a client
  def fbpRuntimeFlow(id: String): Flow[WsMessage, WsMessage, Any] = {
    Flow[WsMessage]
      .mapConcat(readWsMessage)
      .via(subscriptionFlow[Message, Message](id, wsManagerActor))
      .map(writeWsMessage)
  }

  // format: OFF
  val route =
    registrationRoute ~
    pathEndOrSingleSlash {
      provide(UUID.randomUUID().toString.take(8)) { clientId =>
        handleWebSocketMessages {
          logWsMessages(clientId) {
            fbpRuntimeFlow(clientId)
          }
        }
      }
    }
  // format: ON

  Http().bindAndHandle(route, host, port).foreach { binding =>
    log.info(s"Bound to ${binding.localAddress}")
  }
}
