package net.ruippeixotog.scalafbp.thinkbayes

import spray.json._
import thinkbayes.Pmf

trait Implicits extends DefaultJsonProtocol with JsValueMathImplicits {

  implicit class PmfAsPVar[A](pmf: Pmf[A]) {
    def toPVar = PVar(pmf)
  }
}

object Implicits extends Implicits
