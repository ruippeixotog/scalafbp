package net.ruippeixotog.scalafbp.component

import akka.actor.Props
import spray.json.DefaultJsonProtocol._
import spray.json._

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

  implicit val fromShort = fromNumber[Short]
  implicit val fromInt = fromNumber[Int]
  implicit val fromFloat = fromNumber[Float]
  implicit val fromDouble = fromNumber[Double]

  implicit val fromUnit = new PortDataMarshaller[Unit] {
    val typeName = "bang"
    val jsonFormat = new JsonFormat[Unit] {
      def read(json: JsValue) = ()
      def write(obj: Unit) = JsTrue
    }
  }
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
