package net.ruippeixotog.scalafbp

import spray.json._

import net.ruippeixotog.scalafbp.protocol.Message._
import net.ruippeixotog.scalafbp.protocol.RuntimeMessages.GetRuntime
import net.ruippeixotog.scalafbp.protocol._

object Main extends App {
  val json = Runtime(GetRuntime("vsrbsrb")).toJson
  println(json.prettyPrint)
  println(json.convertTo[Message])
}
