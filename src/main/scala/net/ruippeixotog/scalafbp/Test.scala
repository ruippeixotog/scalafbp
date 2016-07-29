package net.ruippeixotog.scalafbp

import spray.json._
import fommil.sjs.FamilyFormats._

object Test extends App {
  sealed trait Message

  object ProtocolA {
    sealed trait ProtocolAMessage extends Message
    case class Start(id: String) extends ProtocolAMessage
  }

  object ProtocolB {
    sealed trait ProtocolBMessage extends Message
    case class Start(id: String) extends ProtocolBMessage
  }

  // prints: { "type": "Start", "id": "abc" }
  // wanted: { "protocol": "ProtocolA", "type": "Start", "id": "abc" }
  println((ProtocolA.Start("abc"): Message).toJson)

  // prints: { "type": "Start", "id": "abc" }
  // wanted: { "protocol": "ProtocolB", "type": "Start", "id": "abc" }
  println((ProtocolB.Start("abc"): Message).toJson)
}
