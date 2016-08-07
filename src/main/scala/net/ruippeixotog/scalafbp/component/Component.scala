package net.ruippeixotog.scalafbp.component

import akka.actor.Props
import spray.json.{ JsValue, JsonFormat }
import spray.json.DefaultJsonProtocol._

trait PortDataMarshaller[T] {
  def typeName: String
  def jsonFormat: JsonFormat[T]
}

object PortDataMarshaller {
  implicit val fromJsValue = new PortDataMarshaller[JsValue] {
    val typeName = "all"
    val jsonFormat = implicitly[JsonFormat[JsValue]]
  }

  private[this] def fromNumber[A: JsonFormat] = new PortDataMarshaller[A] {
    val typeName = "number"
    val jsonFormat = implicitly[JsonFormat[A]]
  }

  implicit def fromShort = fromNumber[Short]
  implicit def fromInt = fromNumber[Int]
  implicit def fromFloat = fromNumber[Float]
  implicit def fromDouble = fromNumber[Double]
}

case class InPort[T](
    id: String,
    description: String,
    required: Boolean = true,
    values: Option[List[T]] = None,
    default: Option[T] = None)(implicit m: PortDataMarshaller[T]) {

  def dataType = m.typeName
  def addressable = false

  def fromJson(js: JsValue): T = m.jsonFormat.read(js)
}

case class OutPort[T](
    id: String,
    description: String,
    required: Boolean = true)(implicit m: PortDataMarshaller[T]) {

  def dataType = m.typeName
  def addressable = false

  def toJson(v: T): JsValue = m.jsonFormat.write(v)
}

trait Component {
  def name: String
  def description: String
  def icon: Option[String]
  def isSubgraph: Boolean
  def inPorts: List[InPort[_]]
  def outPorts: List[OutPort[_]]

  def instanceProps: Props
}
