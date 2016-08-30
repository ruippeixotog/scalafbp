package net.ruippeixotog.scalafbp.http

import java.net.URLDecoder

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.server.Directives._
import spray.json.DefaultJsonProtocol._

trait UiHttpService {

  def disableUi: Boolean

  private[this] val dummyUser = "user"
  private[this] val dummyUserName = "ScalaFBP Development Environment"
  private[this] val dummyToken = "oauthToken"

  // format: OFF
  lazy val uiRoutes =
    if(disableUi) reject
    else {
      pathPrefix("oauth") {
        path("login" / "authorize" / "github_public") {
          parameter("redirect_uri") { encodedUri =>
            val uri = Uri(URLDecoder.decode(encodedUri, "UTF-8"))
            redirect(uri.withQuery(Query("code" -> dummyUser)), Found)
          }
        } ~
        path("authenticate" / dummyUser) {
          complete(Map("token" -> dummyToken))
        } ~
        path("user") {
          complete(Map("name" -> dummyUserName))
        }
      } ~
      pathEndOrSingleSlash { getFromResource("ui/index.html") } ~
      getFromResourceDirectory("ui")
    }
  // format: ON
}
