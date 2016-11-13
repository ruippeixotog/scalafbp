package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.testkit.TestProbe

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.runtime.NetworkBroker.External

class NetworkBrokerExternalsSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "handle correctly communication with external ports" in {

      "route messages targeted at public ports to the external actor" in new BrokerInstance {
        def _graph = graph

        override lazy val externalProbe = TestProbe()

        lazy val graph = new SingleNodeGraph {
          val (_, n1Proxy) = probeBehaviorWithProxyRef(n1)
          (n1, 1) ~> external("extOut")
        }

        graph.n1Proxy ! Outgoing("out1", "test")
        externalProbe must receive(External(Outgoing("extOut", "test")))
      }

      "route messages from the external actor to public source ports" in new BrokerInstance {
        def _graph = graph

        override lazy val externalProbe = TestProbe()

        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
          external("extIn") ~> (n1, 1)
        }

        broker ! External(Incoming("extIn", "test"))
        graph.n1Probe must receive(Incoming("in1", "test")).afterOthers
      }

      "propagate public port disconnection commands to internal components" in new BrokerInstance {
        def _graph = graph

        override lazy val externalProbe = TestProbe()

        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
          external("extIn") ~> (n1, 1) ~> external("extOut")
        }

        broker ! External(InPortDisconnected("extIn"))
        graph.n1Probe must receive(InPortDisconnected("in1")).afterOthers

        broker ! External(OutPortDisconnected("extOut"))
        graph.n1Probe must receive(OutPortDisconnected("out1")).afterOthers
      }

      "propagate internal source and target disconnections to the external actor" in new BrokerInstance {
        def _graph = graph

        override lazy val externalProbe = TestProbe()

        lazy val graph = new SingleNodeGraph {
          val (_, n1Proxy) = probeBehaviorWithProxyRef(n1)
          external("extIn") ~> (n1, 1) ~> external("extOut")
        }

        graph.n1Proxy ! DisconnectInPort("in1")
        externalProbe must receive(External(DisconnectInPort("extIn")))

        graph.n1Proxy ! DisconnectOutPort("out1")
        externalProbe must receive(External(DisconnectOutPort("extOut")))
      }
    }
  }
}
