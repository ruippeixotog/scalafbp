package net.ruippeixotog.scalafbp.http

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.config.Config

import net.ruippeixotog.scalafbp.protocol.registry
import net.ruippeixotog.scalafbp.protocol.registry.RegistryClient

trait RegistrationHttpService {
  def runtimeId: String
  def secret: String

  implicit def system: ActorSystem
  implicit def materializer: Materializer
  implicit def ec: ExecutionContext

  def registryConfig: Config

  private[this] lazy val host = registryConfig.getString("host")
  private[this] lazy val port = registryConfig.getInt("port")
  private[this] lazy val userId = registryConfig.getString("user-id")
  private[this] lazy val authToken = registryConfig.getString("auth-token")

  def handleRegistryResponse(successMsg: String, errorMsg: String => String)(res: HttpResponse) = res match {
    case HttpResponse(code, _, _, _) if code.isSuccess() =>
      Future.successful(OK -> successMsg)

    case HttpResponse(code, _, entity, _) =>
      Unmarshal(entity).to[String].map { InternalServerError -> errorMsg(_) }
  }

  // format: OFF
  val registrationRoute =
    (path("register") & post) {
      complete {
        val runtimeInfo = registry.Runtime("scalafbp", "websocket", s"ws://$host:$port", runtimeId,
          "Scala FBP Runtime", port, userId, secret)

        RegistryClient.register(runtimeInfo, authToken).flatMap {
          handleRegistryResponse(s"Registered as runtime $runtimeId", { err => s"Failed registering runtime: $err" })
        }
      }
    } ~
    (path("unregister") & post) {
      complete {
        RegistryClient.unregister(runtimeId, authToken).flatMap {
          handleRegistryResponse(s"Unegistered runtime $runtimeId", { err => s"Failed unregistering runtime: $err" })
        }
      }
    }
  // format: ON
}
