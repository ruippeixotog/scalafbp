package net.ruippeixotog.scalafbp.component

object ComponentActor {
  case class Incoming(port: String, data: Any)
  case class Outgoing(port: String, data: Any)

  sealed trait Output
  case class Message(message: String) extends Output
  case class PreviewURL(message: String, url: String) extends Output
}
