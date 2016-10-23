package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Actor, ActorSystem, Props }
import akka.testkit.TestProbe
import monocle.macros.GenLens
import spray.json._

import net.ruippeixotog.scalafbp.component.{ Component, DummyComponent, PortDataMarshaller }
import net.ruippeixotog.scalafbp.runtime.GraphLenses._

class GraphTemplate(implicit system: ActorSystem) {
  var graph = Graph("testgraph")

  trait AsRef[A] {
    def inRef(ref: A): PortRef
    def outRef(ref: A): PortRef
  }

  implicit val strIntAsRef = new AsRef[(String, Int)] {
    def inRef(ref: (String, Int)) = PortRef(ref._1, s"in${ref._2}")
    def outRef(ref: (String, Int)) = PortRef(ref._1, s"out${ref._2}")
  }

  implicit val strStrAsRef = new AsRef[(String, String)] {
    def inRef(ref: (String, String)) = PortRef(ref._1, ref._2)
    def outRef(ref: (String, String)) = PortRef(ref._1, ref._2)
  }

  def inRef[R](ref: R)(implicit asRef: AsRef[R]) = asRef.inRef(ref)
  def outRef[R](ref: R)(implicit asRef: AsRef[R]) = asRef.inRef(ref)

  def node[A: PortDataMarshaller](comp: Component): String = {
    val nodeId = s"n${graph.nodes.size + 1}"
    graph = nodeLens(nodeId).set(Some(Node(comp)))(graph)
    nodeId
  }

  def node[A: PortDataMarshaller](in: Int, out: Int, props: Props = Props.empty): String =
    node(DummyComponent[A](in, out, props))

  def probeNode[A: PortDataMarshaller](in: Int, out: Int): TestProbe = {
    val probe = TestProbe()
    node[A](in, out, Props(new Actor {
      def receive = { case msg => probe.ref.forward(msg) }
    }))
    probe
  }

  def behavior(nodeId: String, newProps: Props): Unit = {
    graph = (nodeLensOpt(nodeId) ^|-> GenLens[Node](_.component)).modify {
      case comp: DummyComponent[a] => comp.withProps(newProps)
      case _ => throw new Exception("Cannot change behavior of non-dummy component")
    }(graph)
  }

  def probeBehavior(nodeId: String): TestProbe = {
    val probe = TestProbe()
    behavior(nodeId, Props(new Actor {
      def receive = {
        case msg if sender() == probe.ref => context.parent ! msg
        case msg => probe.ref.forward(msg)
      }
    }))
    probe
  }

  def initial[A: JsonWriter](data: A) = Initial(data.toJson)

  implicit class PimpedInitial(val initial: Initial) {
    def ~>[R: AsRef](tgt: R) = {
      graph = initialLens(inRef(tgt)).set(Some(initial))(graph)
      tgt
    }
  }

  implicit class PimpedRef[R: AsRef](val ref: R) {
    def ~>[R2: AsRef](tgt: R2) = {
      graph = edgeLens(outRef(ref), inRef(tgt)).set(Some(Edge()))(graph)
      tgt
    }

    def <~[R2: AsRef](src: R2) = {
      graph = edgeLens(outRef(src), inRef(ref)).set(Some(Edge()))(graph)
      src
    }

    def <~(initial: Initial) = {
      graph = initialLens(inRef(ref)).set(Some(initial))(graph)
      initial
    }
  }
}

object GraphTemplate {
  implicit def template2Graph(t: GraphTemplate): Graph = t.graph
}
