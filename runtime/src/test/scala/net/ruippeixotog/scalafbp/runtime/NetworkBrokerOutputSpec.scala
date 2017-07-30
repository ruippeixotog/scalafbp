package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.{ Actor, Props }
import spray.json.DefaultJsonProtocol._
import spray.json.JsString

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.runtime.NetworkBroker._

class NetworkBrokerOutputSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "send relevant information to the output actor" in {

      "send Connect, Data and Disconnect messages when an initial value is sent" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
          initial("aaa") ~> (n1, 1)
        }

        outputProbe must receive(Connect(graph.id, None, PortRef(graph.n1, "in1")))
        outputProbe must receive(Data(graph.id, None, PortRef(graph.n1, "in1"), JsString("aaa")))
        outputProbe must receive(Disconnect(graph.id, None, PortRef(graph.n1, "in1")))
      }

      "send Connect messages for every edge when the network starts" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new ThreeNodeGraph {
          (n1, 1) ~> (n2, 1) ~> (n3, 1)
        }

        outputProbe must receive.allOf(
          Connect(graph.id, Some(PortRef(graph.n1, "out1")), PortRef(graph.n2, "in1")),
          Connect(graph.id, Some(PortRef(graph.n2, "out1")), PortRef(graph.n3, "in1")))
      }

      "send Data messages each time a message is routed" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new TwoNodeGraph {
          val (_, n1Proxy) = probeBehaviorWithProxyRef(n1)
          (n1, 1) ~> (n2, 1)
        }

        graph.n1Proxy ! Outgoing("out1", "d")
        outputProbe must receive(
          Data(graph.id, Some(PortRef(graph.n1, "out1")), PortRef(graph.n2, "in1"), JsString("d"))).afterOthers

        graph.n1Proxy ! Outgoing("out1", "e")
        outputProbe must receive(
          Data(graph.id, Some(PortRef(graph.n1, "out1")), PortRef(graph.n2, "in1"), JsString("e")))
      }

      "send Disconnect messages for every closed route" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new ThreeNodeGraph {
          val (_, n2Proxy) = probeBehaviorWithProxyRef(n2)
          (n1, 1) ~> (n2, 1) ~> (n3, 1)
        }

        graph.n2Proxy ! DisconnectInPort("in1")
        outputProbe must receive(
          Disconnect(graph.id, Some(PortRef(graph.n1, "out1")), PortRef(graph.n2, "in1"))).afterOthers

        graph.n2Proxy ! DisconnectOutPort("out1")
        outputProbe must receive(
          Disconnect(graph.id, Some(PortRef(graph.n2, "out1")), PortRef(graph.n3, "in1")))
      }

      "send NodeCommand messages when a component emits output or a command" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new SingleNodeGraph {
          val (_, n1Proxy) = probeBehaviorWithProxyRef(n1)
        }

        graph.n1Proxy ! Message("some text")
        outputProbe must receive(NodeCommand(graph.id, graph.n1, Message("some text"))).afterOthers

        graph.n1Proxy ! PreviewURL("my gif", "http://example.com/image.gif")
        outputProbe must receive(NodeCommand(graph.id, graph.n1, PreviewURL("my gif", "http://example.com/image.gif")))

        graph.n1Proxy ! ChangeIcon("fa-cog")
        outputProbe must receive(NodeCommand(graph.id, graph.n1, ChangeIcon("fa-cog")))
      }

      "send NodeError messages when a component fails" in new BrokerInstance {
        def _graph = graph

        lazy val instanceProps = Props(new Actor {
          def receive = {
            case Incoming("in1", "boom") => throw new Exception("test error")
            case _ => // ignore
          }
        })

        lazy val graph = new SingleNodeGraph {
          behavior(n1, instanceProps)
          initial("boom") ~> (n1, 1)
        }

        outputProbe must receive(NodeError(graph.id, graph.n1, "test error")).afterOthers
      }
    }
  }
}
