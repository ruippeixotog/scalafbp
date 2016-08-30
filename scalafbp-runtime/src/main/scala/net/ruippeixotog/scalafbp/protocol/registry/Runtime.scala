package net.ruippeixotog.scalafbp.protocol.registry

case class Runtime(
  `type`: String,
  protocol: String,
  address: String,
  id: String,
  label: String,
  port: Int,
  user: String,
  secret: String)
