package net.ruippeixotog.scalafbp.component

object ComponentActor {
  case class Incoming(port: String, data: Any)
  case class Outgoing(port: String, data: Any)

  case class Output(
    message: String,
    msgType: String = "message",
    url: Option[String] = None)
}
