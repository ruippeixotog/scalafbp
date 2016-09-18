package net.ruippeixotog.scalafbp.runtime

import akka.pattern.ask
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ TestKit, TestProbe }
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import scala.concurrent.duration._

import org.specs2.matcher.{ MatchResult, Matcher }
import spray.json._

import net.ruippeixotog.scalafbp.component.DummyComponent
import net.ruippeixotog.scalafbp.runtime.GraphStore._

class GraphStoreSpec(implicit env: ExecutionEnv) extends TestKit(ActorSystem()) with SpecificationLike {

  abstract class GraphStoreInstance(
      withNode: Boolean = false,
      withEdge: Boolean = false,
      withInitial: Boolean = false) extends Scope {
    implicit val timeout = Timeout(3.seconds)

    val store = system.actorOf(Props(new GraphStore))

    val testGraphKey = GraphKey("testgraph")
    val testGraph = Graph(testGraphKey.id)
    val missingGraphKey = GraphKey("notexists")

    val testNodeKey = NodeKey(testGraphKey.id, "testnode")
    val testNode = Node(DummyComponent(2, 2))
    val missingNodeKey = NodeKey(testGraphKey.id, "notexists")
    val noPathNodeKey = NodeKey(missingGraphKey.id, testNodeKey.nodeId)

    val testEdgeKey = EdgeKey(testGraphKey.id, PortRef(testNodeKey.nodeId, "out1"), PortRef(testNodeKey.nodeId, "in1"))
    val testEdge = Edge()
    val missingEdgeKey = EdgeKey(testGraphKey.id, PortRef(testNodeKey.nodeId, "out2"), PortRef(testNodeKey.nodeId, "in1"))
    val noPathEdgeKey1 = EdgeKey(missingGraphKey.id, PortRef(testNodeKey.nodeId, "out1"), PortRef(testNodeKey.nodeId, "in1"))
    val noPathEdgeKey2 = EdgeKey(testGraphKey.id, PortRef(missingNodeKey.nodeId, "out1"), PortRef(missingNodeKey.nodeId, "in1"))

    val testInitialKey = InitialKey(testGraphKey.id, PortRef(testNodeKey.nodeId, "out1"))
    val testInitial = Initial(JsNumber(0))
    val missingInitialKey = InitialKey(testGraphKey.id, PortRef(testNodeKey.nodeId, "out2"))
    val noPathInitialKey1 = InitialKey(missingGraphKey.id, PortRef(testNodeKey.nodeId, "out1"))
    val noPathInitialKey2 = InitialKey(testGraphKey.id, PortRef(missingNodeKey.nodeId, "out1"))

    store ! Create(testGraphKey, testGraph)
    if (withNode) store ! Create(testNodeKey, testNode)
    if (withNode && withEdge) store ! Create(testEdgeKey, testEdge)
    if (withNode && withInitial) store ! Create(testInitialKey, testInitial)
  }

  def beStoreError: Matcher[Any] = PartialFunction[Any, MatchResult[Any]] {
    case obj: AnyRef => obj must haveClass[Error[_]]
    case _ => ko
  }

  "A GraphStore" should {

    "allow creating and retrieving graphs" in new GraphStoreInstance {
      val newGraph = Graph(missingGraphKey.id)

      (store ? Get(testGraphKey)) must beEqualTo(Got(testGraphKey, Some(testGraph))).await
      (store ? Get(missingGraphKey)) must beEqualTo(Got(missingGraphKey, None)).await
      (store ? Create(missingGraphKey, newGraph)) must beEqualTo(Created(missingGraphKey, newGraph)).await
      (store ? Get(missingGraphKey)) must beEqualTo(Got(missingGraphKey, Some(newGraph))).await
    }

    "allow updating existing graphs" in new GraphStoreInstance {
      def f(graph: Graph) = graph.copy(nodes = Map("a" -> Node(null)))

      (store ? Update(testGraphKey, f)) must beEqualTo(Updated(testGraphKey, testGraph, f(testGraph))).await
      (store ? Get(testGraphKey)) must beEqualTo(Got(testGraphKey, Some(f(testGraph)))).await
      (store ? Update(missingGraphKey, f)) must beStoreError.await
    }

    "allow deleting existing graphs" in new GraphStoreInstance {
      (store ? Delete(testGraphKey)) must beEqualTo(Deleted(testGraphKey, testGraph)).await
      (store ? Get(testGraphKey)) must beEqualTo(Got(testGraphKey, None)).await
      (store ? Delete(testGraphKey)) must beStoreError.await
      (store ? Delete(missingGraphKey)) must beStoreError.await
    }

    "allow creating and retrieving nodes on existing graphs" in new GraphStoreInstance(true) {
      val newNode = Node(DummyComponent(1, 1))

      (store ? Get(testNodeKey)) must beEqualTo(Got(testNodeKey, Some(testNode))).await
      (store ? Get(missingNodeKey)) must beEqualTo(Got(missingNodeKey, None)).await
      (store ? Create(missingNodeKey, newNode)) must beEqualTo(Created(missingNodeKey, newNode)).await
      (store ? Get(missingNodeKey)) must beEqualTo(Got(missingNodeKey, Some(newNode))).await
      (store ? Get(noPathNodeKey)) must beStoreError.await
      (store ? Create(noPathNodeKey, newNode)) must beStoreError.await
    }

    "allow updating existing nodes" in new GraphStoreInstance(true) {
      def f(node: Node) = node.copy(metadata = Map("a" -> JsNull))

      (store ? Update(testNodeKey, f)) must beEqualTo(Updated(testNodeKey, testNode, f(testNode))).await
      (store ? Get(testNodeKey)) must beEqualTo(Got(testNodeKey, Some(f(testNode)))).await
      (store ? Update(missingNodeKey, f)) must beStoreError.await
      (store ? Update(noPathNodeKey, f)) must beStoreError.await
    }

    "allow deleting existing nodes" in new GraphStoreInstance(true) {
      (store ? Delete(testNodeKey)) must beEqualTo(Deleted(testNodeKey, testNode)).await
      (store ? Get(testNodeKey)) must beEqualTo(Got(testNodeKey, None)).await
      (store ? Delete(testNodeKey)) must beStoreError.await
      (store ? Delete(noPathNodeKey)) must beStoreError.await
    }

    "allow creating and retrieving edges on existing nodes" in new GraphStoreInstance(true, true) {
      val newEdge = Edge()

      (store ? Get(testEdgeKey)) must beEqualTo(Got(testEdgeKey, Some(testEdge))).await
      (store ? Get(missingEdgeKey)) must beEqualTo(Got(missingEdgeKey, None)).await
      (store ? Create(missingEdgeKey, newEdge)) must beEqualTo(Created(missingEdgeKey, newEdge)).await
      (store ? Get(missingEdgeKey)) must beEqualTo(Got(missingEdgeKey, Some(newEdge))).await
      (store ? Get(noPathEdgeKey1)) must beStoreError.await
      (store ? Get(noPathEdgeKey2)) must beStoreError.await
      (store ? Create(noPathEdgeKey1, newEdge)) must beStoreError.await
      (store ? Create(noPathEdgeKey2, newEdge)) must beStoreError.await
    }

    "allow updating existing edges" in new GraphStoreInstance(true, true) {
      def f(edge: Edge) = edge.copy(metadata = Map("a" -> JsNull))

      (store ? Update(testEdgeKey, f)) must beEqualTo(Updated(testEdgeKey, testEdge, f(testEdge))).await
      (store ? Get(testEdgeKey)) must beEqualTo(Got(testEdgeKey, Some(f(testEdge)))).await
      (store ? Update(missingEdgeKey, f)) must beStoreError.await
      (store ? Update(noPathEdgeKey1, f)) must beStoreError.await
    }

    "allow deleting existing edges" in new GraphStoreInstance(true, true) {
      (store ? Delete(testEdgeKey)) must beEqualTo(Deleted(testEdgeKey, testEdge)).await
      (store ? Get(testEdgeKey)) must beEqualTo(Got(testEdgeKey, None)).await
      (store ? Delete(testEdgeKey)) must beStoreError.await
      (store ? Delete(noPathEdgeKey1)) must beStoreError.await
    }

    "allow creating and retrieving initial values on existing nodes" in new GraphStoreInstance(true, withInitial = true) {
      val newInitial = Initial(JsNumber(1))

      (store ? Get(testInitialKey)) must beEqualTo(Got(testInitialKey, Some(testInitial))).await
      (store ? Get(missingInitialKey)) must beEqualTo(Got(missingInitialKey, None)).await
      (store ? Create(missingInitialKey, newInitial)) must beEqualTo(Created(missingInitialKey, newInitial)).await
      (store ? Get(missingInitialKey)) must beEqualTo(Got(missingInitialKey, Some(newInitial))).await
      (store ? Get(noPathInitialKey1)) must beStoreError.await
      (store ? Get(noPathInitialKey2)) must beStoreError.await
      (store ? Create(noPathInitialKey1, newInitial)) must beStoreError.await
      (store ? Create(noPathInitialKey2, newInitial)) must beStoreError.await
    }

    "allow updating existing initial values" in new GraphStoreInstance(true, withInitial = true) {
      def f(initial: Initial) = initial.copy(metadata = Map("a" -> JsNull))

      (store ? Update(testInitialKey, f)) must beEqualTo(Updated(testInitialKey, testInitial, f(testInitial))).await
      (store ? Get(testInitialKey)) must beEqualTo(Got(testInitialKey, Some(f(testInitial)))).await
      (store ? Update(missingInitialKey, f)) must beStoreError.await
      (store ? Update(noPathInitialKey1, f)) must beStoreError.await
    }

    "allow deleting existing initial values" in new GraphStoreInstance(true, withInitial = true) {
      (store ? Delete(testInitialKey)) must beEqualTo(Deleted(testInitialKey, testInitial)).await
      (store ? Get(testInitialKey)) must beEqualTo(Got(testInitialKey, None)).await
      (store ? Delete(testInitialKey)) must beStoreError.await
      (store ? Delete(noPathInitialKey1)) must beStoreError.await
    }

    "update the full graph as new nodes, edges and initials are added" in new GraphStoreInstance(true, true, true) {
      (store ? Get(testGraphKey)).mapTo[Response[Graph]] must beLike[Response[Graph]] {
        case Got(`testGraphKey`, Some(graph)) =>
          graph.nodes.get(testNodeKey.nodeId) must beSome.which { node =>
            node.edges.get(testEdgeKey.src.port) must beSome(Map(testEdgeKey.tgt -> testEdge))
            node.initials.get(testInitialKey.tgt.port) must beSome(testInitial)
          }
      }.await
    }

    "allow adding listeners for events of a graph" in new GraphStoreInstance(true, true, true) {
      val probe = TestProbe()

      val newNode = Node(DummyComponent(1, 1))
      def f(edge: Edge) = edge.copy(metadata = Map("a" -> JsNull))
      def f2(edge: Edge) = edge.copy(metadata = Map("b" -> JsNull))

      store ! Watch(testGraphKey.id, probe.ref)
      store ! Create(missingNodeKey, newNode)
      probe.expectMsg(Event(Created(missingNodeKey, newNode)))
      store ! Update(testEdgeKey, f)
      probe.expectMsg(Event(Updated(testEdgeKey, testEdge, f(testEdge))))

      store ! Unwatch(testGraphKey.id, probe.ref)
      store ! Update(testEdgeKey, f2)
      probe.expectNoMsg()

      store ! Watch(testGraphKey.id, probe.ref)
      store ! Delete(testInitialKey)
      probe.expectMsg(Event(Deleted(testInitialKey, testInitial)))
      store ! Delete(testGraphKey)
      probe.expectMsgPF() { case Event(ev: Deleted[_]) if ev.key == testGraphKey => ok }

      store ! Create(missingGraphKey, Graph(missingGraphKey.id))
      store ! Delete(missingGraphKey)
      probe.expectNoMsg()
    }
  }
}
