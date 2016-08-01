package net.ruippeixotog.scalafbp.http

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import com.typesafe.config.ConfigFactory

import net.ruippeixotog.scalafbp.protocol.MainProtocolActor
import net.ruippeixotog.scalafbp.runtime.LogicActor

object WebServer extends App with WsRuntimeHttpService with RegistrationHttpService {
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

  // all the routes offered by this server
  val routes = registrationRoutes ~ wsRuntimeRoutes

  Http().bindAndHandle(routes, host, port).foreach { binding =>
    log.info(s"Bound to ${binding.localAddress}")
  }
}
