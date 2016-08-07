package net.ruippeixotog.scalafbp.component

import java.net.URI

import scala.util.Try

import spray.json._
import spray.json.DefaultJsonProtocol._

trait PortDataMarshaller[T] {
  def typeName: String
  def jsonFormat: JsonFormat[T]
}

object PortDataMarshaller {

  def forType[A](tName: String)(implicit jf: JsonFormat[A]) = new PortDataMarshaller[A] {
    val typeName = tName
    val jsonFormat = jf
  }

  implicit val unitMarshaller = forType[Unit]("bang")(new JsonFormat[Unit] {
    def read(json: JsValue) = ()
    def write(bang: Unit) = JsTrue // bang is sent as `true` in the UI
  })

  implicit val booleanMarshaller = forType[Boolean]("boolean")
  implicit val shortMarshaller = forType[Short]("int")
  implicit val intMarshaller = forType[Int]("int")
  implicit val floatMarshaller = forType[Float]("number")
  implicit val doubleMarshaller = forType[Double]("number")
  implicit val stringMarshaller = forType[String]("any") // "any" makes the UI do no parsing and send the input as-is
  implicit def mapMarshaller[A: JsonFormat, B: JsonFormat] = forType[Map[A, B]]("object")
  implicit def seqMarshaller[A, MA <: TraversableOnce[A]: JsonFormat] = forType[MA]("array")

  implicit val uriMarshaller = forType[URI]("uri")(new JsonFormat[URI] {
    def write(uri: URI) = JsString(uri.toString)
    def read(json: JsValue) = json match {
      case JsString(str) => new URI(str)
      case x => deserializationError("Expected URI as JsString, but got " + x)
    }
  })

  implicit val jsValueMarshaller = forType[JsValue]("any")(new JsonFormat[JsValue] {
    def write(obj: JsValue) = obj
    def read(json: JsValue) = json match {
      case jsStr @ JsString(str) =>
        // "any" inputs are always sent as unparsed strings; try to parse it into proper JSON
        Try(str.parseJson).getOrElse(jsStr)

      case x => x
    }
  })

  // By default, tell clients the field is an "object". This won't work for any type serialized as anything other than
  // a JsObject. This can be overriden for any Scala type by providing a `PortDataMarshaller` instance for it.
  implicit def defaultMarshaller[A: JsonFormat] = forType[A]("object")
}
