package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Actor, Props }
import akka.testkit.TestProbe
import spray.json.JsString

import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import net.ruippeixotog.scalafbp.component.{ Component, DummyComponent }

class NetworkBrokerSpec extends AkkaSpecification {

  "A NetworkBroker" should {

    "manage the lifetime of the components" in {

      "instantiate all components in a graph when the network starts" in {
        val probe = TestProbe()
        def instanceProps(id: String) = Props(new Actor {
          probe.ref ! s"started_$id"
          def receive = Actor.ignoringBehavior
        })
        val comp1 = DummyComponent(1, 1, instanceProps("n1"))
        val comp2 = DummyComponent(1, 1, instanceProps("n2"))

        val graph = Graph(
          "graph1",
          nodes = Map(
            "n1" -> Node(comp1, edges = Map("out1" -> Map(PortRef("n2", "in1") -> Edge()))),
            "n2" -> Node(comp2, initials = Map("in1" -> Initial(JsString("aaa"))))))

        system.actorOf(NetworkBroker.props(graph, false, system.deadLetters))
        probe must receive.allOf("started_n1", "started_n2")
      }

      "terminate the network when all components terminate" in {
        todo
      }
    }

    "handle initial values correctly" in {

      "forward initial values into components as Incoming messages" in {
        todo
      }

      "handle correctly type conversions" in {
        todo
      }

      "fail if an initial value cannot be converted to the target type" in {
        todo
      }
    }

    "handle data emitted by components correctly" in {

      "forward Outgoing messages to connected ports as Incoming messages" in {
        todo
      }

      "do type conversions between two ports if needed" in {
        todo
      }

      "fail if the outgoing data cannot be converted into the target type" in {
        todo
      }

      "fail if the outgoing data comes from an unknown actor" in {
        todo
      }
    }

    "handle in and out port connections correctly (non-dynamic mode)" in {

      "close a route when its source port is closed by the source component" in {
        todo
      }

      "close a route when its target port is closed by the target component" in {
        todo
      }

      "close source ports when all of its target ports are closed" in {
        todo
      }

      "close target ports when all of its source ports are closed" in {
        todo
      }

      "close all the ports of a component when it terminates" in {
        todo
      }
    }

    "handle in and out port connections correctly (dynamic mode)" in {

      "close a route when its source port is closed by the source component" in {
        todo
      }

      "close a route when its target port is closed by the target component" in {
        todo
      }

      "do not close source ports even when all of its target ports are closed" in {
        todo
      }

      "do not close target ports even when all of its source ports are closed" in {
        todo
      }

      "close all the ports of a component when it terminates" in {
        todo
      }
    }

    "update the network when the underlying graph changes (dynamic mode)" in {

      "create new nodes when a node created event is received" in {
        todo
      }

      "terminate the respective component when a node deleted event is received" in {
        todo
      }

      "open a route when an edge created event is received" in {
        todo
      }

      "close the respective route when an edge deleted event is received" in {
        todo
      }

      "send an initial value when an initial created event is received" in {
        todo
      }

      "stop the network if a graph deleted event is received" in {
        todo
      }
    }

    "handle correctly communication with external ports" in {

      "route messages targeted at public ports to the external actor" in {
        todo
      }

      "route messages from the external actor to public source ports" in {
        todo
      }

      "propagate public port disconnection commands to internal components" in {
        todo
      }

      "propagate internal source and target disconnections to the external actor" in {
        todo
      }
    }

    "send relevant information to the output actor" in {

      "send Connected, Data and Disconnected messages when an initial value is sent" in {
        todo
      }

      "send Connected messages for every edge when the network starts" in {
        todo
      }

      "send Data messages each time a message is routed" in {
        todo
      }

      "send Disconnected messages for every closed route" in {
        todo
      }

      "send Output messages when a components emits output" in {
        todo
      }

      "send ProcessError messages when a component fails" in {
        todo
      }
    }
  }
}
