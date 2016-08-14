package net.ruippeixotog.scalafbp.http

import akka.http.scaladsl.server.Directives._

trait UiHttpService {

  def disableUi: Boolean

  // format: OFF
  lazy val uiRoutes =
    if(disableUi) reject
    else {
      pathEndOrSingleSlash { getFromResource("ui/index.html") } ~
      getFromResourceDirectory("ui")
    }
  // format: ON
}
