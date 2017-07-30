package net.ruippeixotog.scalafbp.runtime

import spray.json._

import net.ruippeixotog.scalafbp.component.core.{ MakeFunction, Output, Repeat }
import net.ruippeixotog.scalafbp.component.{ ComponentSpec, ErrorComponent }

class GraphComponentSpec extends ComponentSpec {
  def component = ??? // must be defined in each example

  abstract class GraphComponentInstance(graph: Graph) extends ComponentInstance {
    override lazy val component = GraphComponent(graph)
  }

  val repeatGraph = Graph(
    "graph1",
    nodes = Map(
      "n1" -> Node(Repeat, edges = Map("out" -> Map(PortRef("n2", "in") -> Edge()))),
      "n2" -> Node(MakeFunction, initials = Map("func" -> Initial(JsString("return x * 2 + 1"))))),
    publicIn = Map("pubIn" -> PublicPort(PortRef("n1", "in"))),
    publicOut = Map("pubOut" -> PublicPort(PortRef("n2", "out"))))

  val outputGraph = Graph(
    "graph2",
    nodes = Map(
      "n1" -> Node(Output, edges = Map("out" -> Map(PortRef("n2", "in") -> Edge()))),
      "n2" -> Node(Output)),
    publicIn = Map("pubIn" -> PublicPort(PortRef("n1", "in"))))

  val nodeErrorGraph = Graph(
    "graph3",
    nodes = Map("n1" -> Node(ErrorComponent)),
    publicIn = Map("pubIn" -> PublicPort(PortRef("n1", "in"))))

  val internalErrorGraph = Graph(
    "graph4",
    nodes = Map(
      "n1" -> Node(Repeat, edges = Map("out" -> Map(PortRef("n2", "func") -> Edge()))),
      "n2" -> Node(MakeFunction)),
    publicIn = Map("pubFunc" -> PublicPort(PortRef("n1", "in"))))

  "A GraphComponent" should {

    "have the correct external interface" in {
      val component = GraphComponent(repeatGraph)

      component.name mustEqual "scalafbp/graph1"
      component.inPorts mustEqual List(Repeat.inPorts("in").withId("pubIn"))
      component.outPorts mustEqual List(MakeFunction.outPorts("out").withId("pubOut"))
    }

    "execute a graph and work as a single component" in new GraphComponentInstance(repeatGraph) {
      component.inPorts("pubIn").send(JsNumber(2))
      component.outPorts("pubOut") must emit(JsNumber(5))

      component.inPorts("pubIn").send(JsNumber(17.5))
      component.outPorts("pubOut") must emit(JsNumber(36))

      this must not(terminate())
    }

    "propagate correctly in port disconnections" in new GraphComponentInstance(repeatGraph) {
      component.inPorts("pubIn").close()
      this must terminate()
    }

    "propagate correctly out port disconnections" in new GraphComponentInstance(repeatGraph) {
      component.outPorts("pubOut").close()
      this must terminate()
    }

    "propagate correctly Output messages" in new GraphComponentInstance(outputGraph) {
      component.inPorts("pubIn").send(JsNumber(2))
      this must sendOutput("2")
      this must sendOutput("2")
    }

    "terminate with a node error if one of the inner components fail" in new GraphComponentInstance(nodeErrorGraph) {
      component.inPorts("pubIn").send(JsTrue)
      this must terminateWithError()
    }

    "terminate with a node error if an internal error occurs" in new GraphComponentInstance(internalErrorGraph) {
      component.inPorts("pubFunc").send(JsTrue)
      this must terminateWithError() // cannot deserialize JsTrue to string
    }
  }
}
