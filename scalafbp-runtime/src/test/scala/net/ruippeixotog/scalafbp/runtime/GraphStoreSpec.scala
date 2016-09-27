package net.ruippeixotog.scalafbp.runtime

import scala.concurrent.duration._
import scala.reflect.ClassTag

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.testkit.{ TestKit, TestProbe }
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{ MatchResult, Matcher }
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import org.specs2.specification.core.Fragment
import spray.json._

import net.ruippeixotog.scalafbp.component.DummyComponent
import net.ruippeixotog.scalafbp.runtime.GraphStore._

class GraphStoreSpec(implicit env: ExecutionEnv) extends TestKit(ActorSystem()) with SpecificationLike {
  implicit val timeout = Timeout(3.seconds)

  trait TestSettings[A] {
    def existingKey: Key[A]
    def missingKey: Key[A]
    def noPathKeys: Seq[Key[A]]
    def existingEntity: A
    def newEntity: A
    def updated(old: A): A

    def init(store: ActorRef) = {
      store ! Create(existingKey, existingEntity)
    }
  }

  abstract class GraphStoreInstance[A](settings: TestSettings[A]) extends Scope {
    val store = system.actorOf(Props(new GraphStore))
    settings.init(store)
  }

  def beStoreError: Matcher[Any] = PartialFunction[Any, MatchResult[Any]] {
    case obj: AnyRef => obj must haveClass[Error[_]]
    case _ => ko
  }

  def allowCrudOperationsOn[A: ClassTag](name: String, settings: TestSettings[A]): Fragment = {
    import settings._

    s"allow creating and retrieving ${name}s" in new GraphStoreInstance(settings) {
      (store ? Get(existingKey)) must beEqualTo(Got(existingKey, Some(existingEntity))).await
      (store ? Get(missingKey)) must beEqualTo(Got(missingKey, None)).await
      (store ? Create(missingKey, newEntity)) must beEqualTo(Created(missingKey, newEntity)).await
      (store ? Get(missingKey)) must beEqualTo(Got(missingKey, Some(newEntity))).await
      forall(noPathKeys) { noPathKey =>
        (store ? Get(noPathKey)) must beStoreError.await
        (store ? Create(noPathKey, newEntity)) must beStoreError.await
      }
    }

    s"allow updating existing ${name}s" in new GraphStoreInstance(settings) {
      (store ? Update(existingKey, updated)) must beEqualTo(Updated(existingKey, existingEntity, updated(existingEntity))).await
      (store ? Get(existingKey)) must beEqualTo(Got(existingKey, Some(updated(existingEntity)))).await
      (store ? Update(missingKey, updated)) must beStoreError.await
      forall(noPathKeys) { noPathKey =>
        (store ? Update(noPathKey, updated)) must beStoreError.await
      }
    }

    s"allow upserting ${name}s" in new GraphStoreInstance(settings) {
      (store ? Upsert(missingKey, newEntity)) must beEqualTo(Created(missingKey, newEntity)).await
      (store ? Get(missingKey)) must beEqualTo(Got(missingKey, Some(newEntity))).await
      (store ? Upsert(existingKey, newEntity)) must beEqualTo(Updated(existingKey, existingEntity, newEntity)).await
      (store ? Get(existingKey)) must beEqualTo(Got(existingKey, Some(newEntity))).await
      forall(noPathKeys) { noPathKey =>
        (store ? Upsert(noPathKey, newEntity)) must beStoreError.await
      }
    }

    settings.existingKey match {
      case _: RenamableKey[A] =>
        implicit def asRenamable(key: Key[A]) = key.asInstanceOf[RenamableKey[A]]

        s"allow renaming existing ${name}s" in new GraphStoreInstance(settings) {
          (store ? Rename(existingKey, missingKey)) must beEqualTo(Renamed(existingKey, missingKey)).await
          (store ? Get(existingKey)) must beEqualTo(Got(existingKey, None)).await
          (store ? Get(missingKey)) must beEqualTo(Got(missingKey, Some(existingEntity))).await
          (store ? Rename(existingKey, missingKey)) must beStoreError.await
          forall(noPathKeys) { noPathKey =>
            (store ? Rename(noPathKey, existingKey)) must beStoreError.await
          }
        }

      case _ => // no test to do here
    }

    s"allow deleting existing ${name}s" in new GraphStoreInstance(settings) {
      (store ? Delete(existingKey)) must beEqualTo(Deleted(existingKey, existingEntity)).await
      (store ? Get(existingKey)) must beEqualTo(Got(existingKey, None)).await
      (store ? Delete(existingKey)) must beStoreError.await
      (store ? Delete(missingKey)) must beStoreError.await
      forall(noPathKeys) { noPathKey =>
        (store ? Delete(noPathKey)) must beStoreError.await
      }
    }

    settings.existingKey match {
      case _: GraphKey =>
        s"allow listening to changes on the lifetime of graphs" in new GraphStoreInstance(settings) {
          val probe = TestProbe()

          store ! Watch("testgraph", probe.ref)
          store ! Update(existingKey, updated)
          probe.expectMsg(Event(Updated(existingKey, existingEntity, updated(existingEntity))))
          store ! Delete(existingKey)
          probe.expectMsg(Event(Deleted(existingKey, updated(existingEntity))))
        }

      case _ =>
        s"inform graph listeners of changes to ${name}s" in new GraphStoreInstance(settings) {
          val probe = TestProbe()

          store ! Watch("testgraph", probe.ref)
          store ! Create(missingKey, newEntity)
          probe.expectMsg(Event(Created(missingKey, newEntity)))
          store ! Update(existingKey, updated)
          probe.expectMsg(Event(Updated(existingKey, existingEntity, updated(existingEntity))))

          store ! Unwatch("testgraph", probe.ref)
          store ! Update(missingKey, updated)
          probe.expectNoMsg()

          store ! Watch("testgraph", probe.ref)
          store ! Delete(existingKey)
          probe.expectMsg(Event(Deleted(existingKey, updated(existingEntity))))
        }
    }
  }

  val graphSettings = new TestSettings[Graph] {
    val existingKey = GraphKey("testgraph")
    val missingKey = GraphKey("missinggraph")
    val noPathKeys = Nil

    val existingEntity = Graph("testgraph")
    val newEntity = Graph("id")
    def updated(graph: Graph) = graph.copy(nodes = Map("a" -> Node(null)))
  }

  val nodeSettings = new TestSettings[Node] {
    val existingKey = NodeKey("testgraph", "testnode")
    val missingKey = NodeKey("testgraph", "missingnode")
    val noPathKeys = List(NodeKey("missinggraph", "testnode"))

    val existingEntity = Node(DummyComponent(2, 2))
    val newEntity = Node(DummyComponent(1, 1))
    def updated(node: Node) = node.copy(metadata = Map("a" -> JsNull))

    override def init(store: ActorRef) = {
      graphSettings.init(store)
      super.init(store)
    }
  }

  val edgeSettings = new TestSettings[Edge] {
    val existingKey = EdgeKey("testgraph", PortRef("testnode", "out1"), PortRef("testnode", "in1"))
    val missingKey = EdgeKey("testgraph", PortRef("testnode", "out2"), PortRef("testnode", "in1"))
    val noPathKeys = List(
      EdgeKey("missinggraph", PortRef("testnode", "out1"), PortRef("testnode", "in1")),
      EdgeKey("testgraph", PortRef("missingnode", "out1"), PortRef("missingnode", "in1")))

    val existingEntity = Edge()
    val newEntity = Edge(Map("a" -> JsTrue))
    def updated(edge: Edge) = edge.copy(metadata = Map("a" -> JsNull))

    override def init(store: ActorRef) = {
      nodeSettings.init(store)
      super.init(store)
    }
  }

  val initialSettings = new TestSettings[Initial] {
    val existingKey = InitialKey("testgraph", PortRef("testnode", "in1"))
    val missingKey = InitialKey("testgraph", PortRef("testnode", "in2"))
    val noPathKeys = List(
      InitialKey("missinggraph", PortRef("testnode", "in1")),
      InitialKey("testgraph", PortRef("missingnode", "in1")))

    val existingEntity = Initial(JsNumber(0))
    val newEntity = Initial(JsNumber(1))
    def updated(initial: Initial) = initial.copy(metadata = Map("a" -> JsNull))

    override def init(store: ActorRef) = {
      nodeSettings.init(store)
      super.init(store)
    }
  }

  val publicInPortSettings = new TestSettings[PublicPort] {
    val existingKey = PublicInPortKey("testgraph", "pubIn1")
    val missingKey = PublicInPortKey("testgraph", "pubIn2")
    val noPathKeys = List(PublicInPortKey("missinggraph", "pubIn1"))

    val existingEntity = PublicPort(PortRef("testnode", "in1"))
    val newEntity = PublicPort(PortRef("testnode", "in2"))
    def updated(port: PublicPort) = port.copy(metadata = Map("a" -> JsNull))

    override def init(store: ActorRef) = {
      nodeSettings.init(store)
      super.init(store)
    }
  }

  val publicOutPortSettings = new TestSettings[PublicPort] {
    val existingKey = PublicOutPortKey("testgraph", "pubOut1")
    val missingKey = PublicOutPortKey("testgraph", "pubOut2")
    val noPathKeys = List(PublicOutPortKey("missinggraph", "pubOut1"))

    val existingEntity = PublicPort(PortRef("testnode", "out1"))
    val newEntity = PublicPort(PortRef("testnode", "out2"))
    def updated(port: PublicPort) = port.copy(metadata = Map("a" -> JsNull))

    override def init(store: ActorRef) = {
      nodeSettings.init(store)
      super.init(store)
    }
  }

  "A GraphStore" should {

    allowCrudOperationsOn("graph", graphSettings)
    allowCrudOperationsOn("node", nodeSettings)
    allowCrudOperationsOn("edge", edgeSettings)

    "update correctly the edges when a node is renamed" in new GraphStoreInstance(edgeSettings) {
      import edgeSettings._

      val existingNodeKey = nodeSettings.existingKey
      val missingNodeKey = nodeSettings.missingKey

      val renamedEdgeKey = existingKey.copy(
        src = existingKey.src.copy(node = missingNodeKey.nodeId),
        tgt = existingKey.tgt.copy(node = missingNodeKey.nodeId))

      (store ? Rename(existingNodeKey, missingNodeKey)) must beEqualTo(Renamed(existingNodeKey, missingNodeKey)).await
      (store ? Get(existingKey)) must beStoreError.await
      (store ? Get(renamedEdgeKey)) must beEqualTo(Got(renamedEdgeKey, Some(existingEntity))).await
    }

    allowCrudOperationsOn("initial value", initialSettings)
    allowCrudOperationsOn("public in port", publicInPortSettings)
    allowCrudOperationsOn("public out port", publicOutPortSettings)

    "update the full graph as new contents are added" in {
      val store = system.actorOf(Props(new GraphStore))
      store ! Create(GraphKey("testgraph"), Graph("testgraph"))
      store ! Create(NodeKey("testgraph", "testnode"), Node(DummyComponent(1, 1)))
      store ! Create(EdgeKey("testgraph", PortRef("testnode", "out1"), PortRef("testnode", "in1")), Edge())
      store ! Create(InitialKey("testgraph", PortRef("testnode", "in1")), Initial(JsTrue))
      store ! Create(PublicInPortKey("testgraph", "pubIn1"), PublicPort(PortRef("testnode", "in1")))
      store ! Create(PublicOutPortKey("testgraph", "pubOut1"), PublicPort(PortRef("testnode", "out1")))

      val fullGraph = Graph(
        "testgraph",
        nodes = Map("testnode" -> Node(
          DummyComponent(1, 1),
          edges = Map("out1" -> Map(PortRef("testnode", "in1") -> Edge())),
          initials = Map("in1" -> Initial(JsTrue)))),
        publicIn = Map("pubIn1" -> PublicPort(PortRef("testnode", "in1"))),
        publicOut = Map("pubOut1" -> PublicPort(PortRef("testnode", "out1"))))

      (store ? Get(GraphKey("testgraph"))).mapTo[Response[Graph]] must beLike[Response[Graph]] {
        case Got(_, Some(graph)) => graph mustEqual fullGraph
      }.await
    }
  }
}
