package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.{ Actor, Props, Terminated }
import akka.testkit.TestProbe
import spray.json.JsString

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing }
import net.ruippeixotog.scalafbp.component.DummyComponent
import net.ruippeixotog.scalafbp.runtime.GraphStore._
import net.ruippeixotog.scalafbp.runtime.Store._

class NetworkBrokerUpdatesSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "update the network when the underlying graph changes (dynamic mode)" in {

      "create new nodes when a node created event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val graph = new SingleNodeGraph

        val probe = TestProbe()
        val instanceProps = Props(new Actor {
          probe.ref ! "started"
          def receive = Actor.ignoringBehavior
        })

        val node = Node(DummyComponent[String](1, 1, instanceProps))
        broker ! Event(Created(NodeKey(graph.id, "newnode"), node))
        probe must receive("started")
      }

      "terminate the respective component when a node deleted event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val probe = TestProbe()
        lazy val instanceProps = Props(new Actor {
          def receive = Actor.ignoringBehavior
          override def postStop() = probe.ref ! "stopped"
        })

        lazy val graph = new SingleNodeGraph {
          behavior(n1, instanceProps)
        }

        probe must not(receive("stopped"))

        broker ! Event(Deleted(NodeKey(graph.id, graph.n1), null))
        probe must receive("stopped")
      }

      "open a route when an edge created event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val graph = new TwoNodeGraph {
          val (n1Probe, n1Proxy) = probeBehaviorWithProxyRef(n1)
          val n2Probe = probeBehavior(n2)
        }

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must not(receive(Incoming("in1", "init")).afterOthers)

        broker ! Event(Created(EdgeKey(graph.id, PortRef(graph.n1, "out1"), PortRef(graph.n2, "in1")), Edge()))
        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must receive(Incoming("in1", "init")).afterOthers
      }

      "close the respective route when an edge deleted event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val graph = new TwoNodeGraph {
          val (n1Probe, n1Proxy) = probeBehaviorWithProxyRef(n1)
          val n2Probe = probeBehavior(n2)
          (n1, 1) ~> (n2, 1)
        }

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must receive(Incoming("in1", "init")).afterOthers

        broker ! Event(Deleted(EdgeKey(graph.id, PortRef(graph.n1, "out1"), PortRef(graph.n2, "in1")), null))
        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must not(receive(Incoming("in1", "init")).afterOthers)
      }

      "send an initial value when an initial created event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
        }

        broker ! Event(Created(InitialKey(graph.id, PortRef(graph.n1, "in1")), Initial(JsString("here"))))
        graph.n1Probe must receive(Incoming("in1", "here")).afterOthers
      }

      "stop the network if a graph deleted event is received" in new BrokerInstance(true) {
        def _graph = graph

        lazy val graph = new SingleNodeGraph

        broker ! Event(Deleted(GraphKey(graph.id), null))
        lifeProbe must receive.like { case Terminated(`broker`) => ok }
      }
    }
  }
}
