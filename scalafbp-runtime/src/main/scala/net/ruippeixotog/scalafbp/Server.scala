package net.ruippeixotog.scalafbp

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import com.typesafe.config.ConfigFactory

import net.ruippeixotog.scalafbp.http._
import net.ruippeixotog.scalafbp.protocol.MainProtocolActor
import net.ruippeixotog.scalafbp.runtime.{ DefaultComponentRegistry, GraphStore }

object Server extends App with WsRuntimeHttpService with RegisterHttpService with RegistryHttpService
    with UiHttpService {

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
  val runtimeConfig = config.getConfig("runtime")

  val runtimeId = config.getString("runtime-id")
  val secret = config.getString("secret")

  val host = config.getString("host")
  val port = config.getInt("port")

  val disableUi = config.getBoolean("disable-ui")

  // the registry of components that will be made available to clients
  val compRegistry = DefaultComponentRegistry

  // an object responsible for storing and managing the graph definitions currently in the runtime
  val graphStore = system.actorOf(Props(new GraphStore))

  // actor that receives incoming messages (as `Message` objects) and translates them into actions using the above
  // constructs
  val protocolActor = system.actorOf(Props(
    new MainProtocolActor(runtimeId, secret, compRegistry, graphStore, runtimeConfig)))

  // all the routes offered by this server
  val routes = registrationRoutes ~ registryRoutes ~ wsRuntimeRoutes ~ uiRoutes

  Http().bindAndHandle(routes, host, port).foreach { binding =>
    log.info(s"Bound to ${binding.localAddress}")
    onBind(binding)
  }
}
