package net.ruippeixotog.scalafbp.component

object ComponentActor {
  case class Incoming(name: String, data: Any)
  case class Outgoing(name: String, data: Any)
}
