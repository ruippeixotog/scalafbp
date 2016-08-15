package net.ruippeixotog.scalafbp.util

import javax.script._

import spray.json._

trait NashornEngine {

  private[this] val engine =
    Option(new ScriptEngineManager().getEngineByName("nashorn")) match {
      case Some(e: ScriptEngine with Compilable with Invocable) => e
      case _ => throw new UnsupportedOperationException("JavaScript compilation is not supported by this JVM")
    }

  private[this] def wrapJsCode(body: String, varNames: List[String]) = {
    val varList = varNames.mkString(",")
    val parsedVarList = varNames.map("JSON.parse(" + _ + ")").mkString(",")
    s"""
       |function apply($varList) { return JSON.stringify(__execute($parsedVarList)); }
       |function __execute($varList) { $body }
      """.stripMargin
  }

  type JsFunction = JsValue => JsValue
  object JsFunction {
    private[this] trait JsFunctionInner {
      def apply(input: String): String
    }

    def apply(body: String, varName: String = "x"): JsFunction = {
      engine.compile(wrapJsCode(body, List(varName))).eval()
      val f = engine.getInterface(classOf[JsFunctionInner])

      { jsValue: JsValue => f(jsValue.compactPrint).parseJson }
    }
  }

  type JsFunction2 = (JsValue, JsValue) => JsValue
  object JsFunction2 {
    private[this] trait JsFunction2Inner {
      def apply(input1: String, input2: String): String
    }

    def apply(body: String, varName1: String = "x", varName2: String = "y"): JsFunction2 = {
      engine.compile(wrapJsCode(body, List(varName1, varName2))).eval()
      val f = engine.getInterface(classOf[JsFunction2Inner])

      { (jsValue1: JsValue, jsValue2: JsValue) => f(jsValue1.compactPrint, jsValue2.compactPrint).parseJson }
    }
  }
}
