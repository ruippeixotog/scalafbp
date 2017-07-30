package net.ruippeixotog.scalafbp.thinkbayes

import spray.json._
import spray.json.DefaultJsonProtocol._
import thinkbayes.Pmf

import net.ruippeixotog.scalafbp.component.PortDataMarshaller

trait PmfJsonProtocol {

  implicit def pmfJsonFormat[K: JsonFormat] = new RootJsonFormat[Pmf[K]] {
    def read(json: JsValue) = Pmf(json.convertTo[Map[String, Double]]).mapKeys(_.parseJson.convertTo[K])
    def write(obj: Pmf[K]) = obj.mapKeys(_.toJson.compactPrint).toMap.toJson
  }

  implicit def pmfMarshaller[K: JsonFormat] = PortDataMarshaller.forType[Pmf[K]]("object")
}

object PmfJsonProtocol extends PmfJsonProtocol
