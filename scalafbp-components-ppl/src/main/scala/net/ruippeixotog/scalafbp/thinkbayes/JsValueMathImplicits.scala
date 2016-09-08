package net.ruippeixotog.scalafbp.thinkbayes

import scala.math.Ordered

import spray.json._

import net.ruippeixotog.scalafbp.component.PortDataMarshaller

trait JsValueMathImplicits {

  implicit object JsNumberJsonFormat extends JsonFormat[JsNumber] {
    def write(obj: JsNumber) = obj
    def read(json: JsValue) = json match {
      case js: JsNumber => js
      case x => deserializationError("Expected JsNumber, but got " + x)
    }
  }

  implicit val jsNumberMarshaller = PortDataMarshaller.forType[JsNumber]("number")(new JsonFormat[JsNumber] {
    def write(obj: JsNumber) = obj
    def read(json: JsValue) = json match {
      case js: JsNumber => js
      case x => deserializationError("Expected JsNumber, but got " + x)
    }
  })

  implicit object JsNumberNumeric extends Numeric[JsNumber] {
    def plus(x: JsNumber, y: JsNumber) = JsNumber(x.value + y.value)
    def minus(x: JsNumber, y: JsNumber) = JsNumber(x.value - y.value)
    def times(x: JsNumber, y: JsNumber) = JsNumber(x.value * y.value)
    def negate(x: JsNumber) = JsNumber(-x.value)
    def fromInt(x: Int) = JsNumber(x)
    def toInt(x: JsNumber) = x.value.intValue
    def toLong(x: JsNumber) = x.value.longValue
    def toFloat(x: JsNumber) = x.value.floatValue
    def toDouble(x: JsNumber) = x.value.doubleValue
    def compare(x: JsNumber, y: JsNumber) = x.value.compare(y.value)
  }

  implicit object JsValueOrdering extends Ordering[JsValue] {
    def compare(x: JsValue, y: JsValue) = (x, y) match {
      case (JsNumber(xn), JsNumber(yn)) => xn.compare(yn)
      case (JsString(xs), JsString(ys)) => xs.compareTo(ys)
      case (JsBoolean(xb), JsBoolean(yb)) => xb.compareTo(yb)
      case _ => x.toString.compare(y.toString)
    }
  }

  case class JsValueOrdered(x: JsValue) extends Ordered[JsValue] {
    def compare(that: JsValue): Int = JsValueOrdering.compare(x, that)
    override def toString = x.toString
  }

  implicit def jsValueAsOrdered(x: JsValue) = JsValueOrdered(x)
}
