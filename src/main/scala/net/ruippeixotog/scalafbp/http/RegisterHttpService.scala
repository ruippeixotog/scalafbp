package net.ruippeixotog.scalafbp.http

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.config.Config

import net.ruippeixotog.scalafbp.Server._
import net.ruippeixotog.scalafbp.protocol.registry
import net.ruippeixotog.scalafbp.protocol.registry.RegistryClient

trait RegisterHttpService {

  implicit def system: ActorSystem
  implicit def materializer: Materializer
  implicit def ec: ExecutionContext

  def runtimeId: String
  def secret: String

  def registryConfig: Config

  private[this] lazy val baseUrl = registryConfig.getString("base-url")
  private[this] lazy val host = registryConfig.getString("host")
  private[this] lazy val port = registryConfig.getInt("port")
  private[this] lazy val userId = registryConfig.getString("user-id")
  private[this] lazy val authToken = registryConfig.getString("auth-token")
  private[this] lazy val autoRegister = registryConfig.getBoolean("auto-register")

  private[this] lazy val registryClient = new RegistryClient(baseUrl)

  private[this] lazy val runtimeInfo = registry.Runtime(
    "scalafbp", "websocket", s"ws://$host:$port", runtimeId, "Scala FBP Runtime", port, userId, secret)

  private[this] def handleRegistryResponse(successMsg: String, errorMsg: String => String)(res: HttpResponse) =
    res match {
      case HttpResponse(code, _, _, _) if code.isSuccess() =>
        Future.successful(OK -> successMsg)

      case HttpResponse(code, _, entity, _) =>
        Unmarshal(entity).to[String].map { InternalServerError -> errorMsg(_) }
    }

  // format: OFF
  lazy val registrationRoutes =
    (path("register") & post) {
      complete {
        registryClient.register(runtimeInfo, authToken).flatMap {
          handleRegistryResponse(s"Registered as runtime $runtimeId", { err => s"Failed registering runtime: $err" })
        }
      }
    } ~
    (path("unregister") & post) {
      complete {
        registryClient.unregister(runtimeId, authToken).flatMap {
          handleRegistryResponse(s"Unegistered runtime $runtimeId", { err => s"Failed unregistering runtime: $err" })
        }
      }
    }
  // format: ON

  def onBind(binding: Http.ServerBinding): Unit = {
    if (autoRegister) {
      registryClient.register(runtimeInfo, authToken).flatMap { res =>
        val handler = handleRegistryResponse(
          s"Auto registered as runtime $runtimeId",
          { err => s"Failed registering runtime: $err" }) _

        handler(res).map { r => log.info(r._2) }
      }
    }
  }
}
