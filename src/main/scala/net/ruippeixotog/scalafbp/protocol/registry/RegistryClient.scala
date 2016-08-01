package net.ruippeixotog.scalafbp.protocol.registry

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RequestEntity }
import akka.stream.Materializer
import fommil.sjs.FamilyFormats._

object RegistryClient extends SLF4JLogging {

  def register(runtime: Runtime, token: String)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[HttpResponse] = {
    Marshal(runtime).to[RequestEntity].flatMap { entity =>
      Http().singleRequest(HttpRequest(
        PUT,
        s"http://api.flowhub.io/runtimes/${runtime.id}",
        List(Authorization(OAuth2BearerToken(token))),
        entity))
    }
  }

  def unregister(runtimeId: String, token: String)(implicit system: ActorSystem, mat: Materializer): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      DELETE,
      s"http://api.flowhub.io/runtimes/$runtimeId",
      List(Authorization(OAuth2BearerToken(token)))))
  }
}
