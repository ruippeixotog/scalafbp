package net.ruippeixotog.scalafbp.protocol.registry

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.event.slf4j.SLF4JLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity }
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import fommil.sjs.FamilyFormats._

object RegistryClient extends SLF4JLogging {

  def register(runtime: Runtime)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[Boolean] = {
    Marshal(runtime).to[RequestEntity].flatMap { entity =>
      Http().singleRequest(HttpRequest(
        uri = s"http://api.flowhub.io/runtimes/${runtime.id}",
        method = HttpMethods.PUT,
        entity = entity))
    }.map { res =>
      res.entity.dataBytes.runWith(Sink.ignore)
      if (res.status.isSuccess()) {
        log.info(s"Registered as runtime ${runtime.id}")
      } else {
        log.warn(s"Failed registering runtime: $res")
      }
      res.status.isSuccess()
    }
  }
}
