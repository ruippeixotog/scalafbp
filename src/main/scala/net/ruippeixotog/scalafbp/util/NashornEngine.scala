package net.ruippeixotog.scalafbp.util

import javax.script._

import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import spray.json._

trait JsFunctionInner {
  def apply(input: String): String
}

trait NashornEngine {

  private[this] val engine =
    Option(new NashornScriptEngineFactory().getScriptEngine("nashorn")) match {
      case Some(e: ScriptEngine with Compilable with Invocable) => e
      case _ => throw new UnsupportedOperationException("JavaScript compilation is not supported by this system")
    }

  type JsFunction = JsValue => JsValue
  object JsFunction {
    private[this] def wrapJsCode(body: String, varName: String) =
      s"""
        |function apply(input) { return JSON.stringify(__execute(JSON.parse(input))); }
        |function __execute($varName) { $body }
      """.stripMargin

    def apply(body: String, varName: String = "x"): JsValue => JsValue = {
      engine.compile(wrapJsCode(body, varName)).eval()
      val f = engine.getInterface(classOf[JsFunctionInner])

      { jsValue: JsValue => f(jsValue.compactPrint).parseJson }
    }
  }
}
