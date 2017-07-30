package net.ruippeixotog.scalafbp.thinkbayes

import scala.util.Try

import spray.json._
import thinkbayes.Pmf

import net.ruippeixotog.scalafbp.component.PortDataMarshaller
import net.ruippeixotog.scalafbp.thinkbayes.PmfJsonProtocol._

sealed trait PVar[K] {
  def toPmf: Pmf[K]
}

case class PDistrib[K](pmf: Pmf[K]) extends PVar[K] {
  def toPmf = pmf
}

case class PConst[K](v: K) extends PVar[K] {
  def toPmf = Pmf(v -> 1.0)
}

object PVar {
  def apply[K](pmf: Pmf[K]): PVar[K] = PDistrib(pmf)
  def const[K](v: K): PVar[K] = PConst(v)

  def lift[T1, R](f: T1 => Pmf[R]): PVar[T1] => PVar[R] = {
    case PConst(v1) => PDistrib(f(v1))
    case PDistrib(pmf1) => PDistrib(pmf1.mapKeys(f).mixture)
  }

  def lift[T1, T2, R](f: (T1, T2) => Pmf[R]): (PVar[T1], PVar[T2]) => PVar[R] = {
    case (PConst(v1), PConst(v2)) => PDistrib(f(v1, v2))
    case (PConst(v1), PDistrib(pmf2)) => PDistrib(pmf2.mapKeys(f(v1, _)).mixture)
    case (PDistrib(pmf1), PConst(v2)) => PDistrib(pmf1.mapKeys(f(_, v2)).mixture)
    case (PDistrib(pmf1), PDistrib(pmf2)) => PDistrib(pmf1.join(pmf2)(f).mixture)
  }

  implicit def pVarJsonFormat[K: JsonFormat] = new RootJsonFormat[PVar[K]] {
    def read(json: JsValue) = {
      val parsed = PortDataMarshaller.jsValueMarshaller.jsonFormat.read(json)
      Try(PDistrib(Pmf(parsed.convertTo[Pmf[K]])))
        .getOrElse(PConst(parsed.convertTo[K]))
    }

    def write(obj: PVar[K]) = obj match {
      case PConst(v) => v.toJson
      case PDistrib(pmf) => pmf.toJson
    }
  }

  implicit def pVarMarshaller[K: JsonFormat] = PortDataMarshaller.forType[PVar[K]]("any")
}
