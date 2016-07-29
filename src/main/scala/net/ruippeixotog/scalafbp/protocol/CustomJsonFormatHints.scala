package net.ruippeixotog.scalafbp.protocol

import fommil.sjs._
import shapeless.Typeable
import spray.json.{ JsObject, JsString }

trait CustomJsonFormatHints extends JsonFormatHints {

  trait LowerCaseHints[T] extends CoproductHint[T] {
    override protected def fieldName(orig: String) = orig.toLowerCase
  }

  class MessageCoproductHint(protocolKey: String, commandKey: String, payloadKey: String)(implicit t: Typeable[Message])
      extends CoproductHint[Message] with LowerCaseHints[Message] {

    def read[Name <: Symbol](j: JsObject, n: Name): Option[JsObject] = {
      j.fields.get(protocolKey) match {
        case Some(JsString(hint)) if hint == fieldName(n.name) =>
          j.fields.get(payloadKey) match {
            case Some(obj: JsObject) =>
              j.fields.get(commandKey) match {
                case Some(cmd) => Some(JsObject(payloadKey -> JsObject(obj.fields + (commandKey -> cmd))))
                case None => deserError(s"missing $commandKey, found ${j.fields.keys.mkString(",")}")
              }
            case Some(js) =>
              j.fields.get(commandKey) match {
                case Some(cmd) => Some(JsObject(payloadKey -> JsObject("_value" -> js, commandKey -> cmd)))
                case None => deserError(s"missing $commandKey, found ${j.fields.keys.mkString(",")}")
              }
            case None => deserError(s"missing $payloadKey, found ${j.fields.keys.mkString(",")}")
          }
        case Some(JsString(hint)) => None
        case _ => deserError(s"missing $commandKey, found ${j.fields.keys.mkString(",")}")
      }
    }

    def write[Name <: Symbol](j: JsObject, n: Name): JsObject = {
      val payloadJson = j.fields(payloadKey).asJsObject
      val payloadValue = payloadJson.fields.getOrElse("_value", JsObject(payloadJson.fields - commandKey))
      JsObject(
        protocolKey -> JsString(fieldName(n.name)),
        commandKey -> payloadJson.fields(commandKey),
        payloadKey -> payloadValue)
    }
  }

  implicit override def coproductHint[T: Typeable] =
    new FlatCoproductHint[T]("command") with LowerCaseHints[T]
}

object CustomJsonFormatHints extends CustomJsonFormatHints
