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

class RegistryClient(baseUrl: String = "http://api.flowhub.io") extends SLF4JLogging {

  def register(runtime: Runtime, token: String)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[HttpResponse] = {
    log.debug(s"PUT $baseUrl/runtimes/${runtime.id}")
    Marshal(runtime).to[RequestEntity].flatMap { entity =>
      Http().singleRequest(HttpRequest(
        PUT,
        s"$baseUrl/runtimes/${runtime.id}",
        List(Authorization(OAuth2BearerToken(token))),
        entity))
    }
  }

  def unregister(runtimeId: String, token: String)(implicit system: ActorSystem, mat: Materializer): Future[HttpResponse] = {
    log.debug(s"DELETE $baseUrl/runtimes/$runtimeId")
    Http().singleRequest(HttpRequest(
      DELETE,
      s"$baseUrl/runtimes/$runtimeId",
      List(Authorization(OAuth2BearerToken(token)))))
  }
}
