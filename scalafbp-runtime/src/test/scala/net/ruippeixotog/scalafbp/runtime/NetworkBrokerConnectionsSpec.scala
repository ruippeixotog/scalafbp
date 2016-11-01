package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.{ Actor, Props }

import net.ruippeixotog.scalafbp.component.ComponentActor._

class NetworkBrokerConnectionsSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "handle in and out port connections correctly (non-dynamic mode)" in {

      "close a route when its source port is closed by the source component" in new BrokerInstance {
        lazy val graph = new TwoNodeGraph {
          val (n1Probe, n1Proxy) = probeBehaviorWithProxyRef(n1)
          val n2Probe = probeBehavior(n2)
          (n1, 1) ~> (n2, 1)
        }

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must receive(Incoming("in1", "init")).afterOthers

        graph.n1Proxy ! DisconnectOutPort("out1")
        graph.n1Probe must receive(OutPortDisconnected("out1")).afterOthers

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must not(receive(Incoming("in1", "init")).afterOthers)
      }

      "close a route when its target port is closed by the target component" in new BrokerInstance {
        lazy val graph = new TwoNodeGraph {
          val (n1Probe, n1Proxy) = probeBehaviorWithProxyRef(n1)
          val (n2Probe, n2Proxy) = probeBehaviorWithProxyRef(n2)
          (n1, 1) ~> (n2, 1)
        }

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must receive(Incoming("in1", "init")).afterOthers

        graph.n2Proxy ! DisconnectInPort("in1")
        graph.n1Probe must receive(InPortDisconnected("in1")).afterOthers

        graph.n1Proxy ! Outgoing("out1", "init")
        graph.n2Probe must not(receive(Incoming("in1", "init")).afterOthers)
      }

      "close source ports when all of its target ports are closed" in new BrokerInstance {
        lazy val graph = new TwoToTwoGraph {
          val (_, out1Proxy) = probeBehaviorWithProxyRef(outNode1)
          val (_, out2Proxy) = probeBehaviorWithProxyRef(outNode2)
          val in1Probe = probeBehavior(inNode1)
        }

        graph.out1Proxy ! DisconnectInPort("in1")
        graph.in1Probe must not(receive(OutPortDisconnected("out1")).afterOthers)

        graph.out2Proxy ! DisconnectInPort("in1")
        graph.in1Probe must receive(OutPortDisconnected("out1")).afterOthers
      }

      "close target ports when all of its source ports are closed" in new BrokerInstance {
        lazy val graph = new TwoToTwoGraph {
          val (_, in1Proxy) = probeBehaviorWithProxyRef(inNode1)
          val (_, in2Proxy) = probeBehaviorWithProxyRef(inNode2)
          val out1Probe = probeBehavior(outNode1)
        }

        graph.in1Proxy ! DisconnectOutPort("out1")
        graph.out1Probe must not(receive(InPortDisconnected("in1")).afterOthers)

        graph.in2Proxy ! DisconnectOutPort("out1")
        graph.out1Probe must receive(InPortDisconnected("in1")).afterOthers
      }

      "close all the ports of a component when it terminates" in new BrokerInstance {

        lazy val instanceProps = Props(new Actor {
          context.stop(self)
          def receive = Actor.ignoringBehavior
        })

        lazy val graph = new ThreeNodeGraph {
          (n1, 1) ~> (n2, 1) ~> (n3, 1)
          val n1Probe = probeBehavior(n1)
          behavior(n2, instanceProps)
          val n3Probe = probeBehavior(n3)
        }

        graph.n1Probe must receive(OutPortDisconnected("out1")).afterOthers
        graph.n3Probe must receive(InPortDisconnected("in1")).afterOthers
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
  }
}
