package net.ruippeixotog.scalafbp.http

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import fommil.sjs.FamilyFormats._

import net.ruippeixotog.scalafbp.protocol.registry.Runtime

trait RegistryHttpService {

  implicit def system: ActorSystem
  implicit def materializer: Materializer
  implicit def ec: ExecutionContext

  var runtimes = Map[String, Runtime]()

  def withExistingRuntime(id: String): Directive1[Option[Runtime]] = provide(runtimes.get(id))

  // format: OFF
  lazy val registryRoutes =
    (pathPrefix("registry" / "runtimes") & cors()) {
      pathEndOrSingleSlash {
        get {
          complete(runtimes.values.toList)
        } ~
        post {
          entity(as[Runtime]) { runtime =>
            withExistingRuntime(runtime.id) {
              case None =>
                runtimes += (runtime.id -> runtime)
                complete(Created)

              case Some(_) =>
                complete(Forbidden)
            }
          }
        }
      } ~
      path(Segment) { id =>
        put {
          entity(as[Runtime]) { runtime =>
            if(runtime.id == id) {
              runtimes += (id -> runtime)
              complete(Created)
            } else {
              complete(Forbidden)
            }
          }
        } ~
        delete {
          withExistingRuntime(id) {
            case Some(_) =>
              runtimes -= id
              complete(OK)

            case None =>
              complete(Forbidden)
          }
        }
      }
    }
  // format: ON
}
