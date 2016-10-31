package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.{ Actor, Props, Terminated }
import akka.testkit.TestProbe
import org.specs2.specification.Scope
import spray.json.DefaultJsonProtocol._
import spray.json._

import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.PortDataMarshaller
import net.ruippeixotog.scalafbp.component.core.Repeat
import net.ruippeixotog.scalafbp.runtime.GraphTemplate._

class NetworkBrokerSpec extends AkkaSpecification {

  class SingleNodeGraph extends GraphTemplate {
    val n1 = node[String](1, 1)
  }

  class TwoNodeGraph extends GraphTemplate {
    val n1, n2 = node[String](1, 1)
  }

  class ChainGraph[A: PortDataMarshaller] extends GraphTemplate {
    val inNode = node(Repeat)
    val outNode = node[A](1, 1)
    initial("init") ~> (inNode, "in")
    (inNode, "out") ~> (outNode, 1)
  }

  class TwoToTwoGraph extends GraphTemplate {
    val nodeIn1, nodeIn2, nodeOut1, nodeOut2 = node[String](1, 1)
    (nodeIn1, 1) ~> (nodeOut1, 1) <~ (nodeIn2, 1)
    (nodeIn1, 1) ~> (nodeOut2, 1) <~ (nodeIn2, 1)
  }

  abstract class BrokerInstance extends Scope {
    def graph: GraphTemplate
    def enableExternal = false

    val lifeProbe, outputProbe = TestProbe()
    val broker = system.actorOf(NetworkBroker.props(graph, false, outputProbe.ref))
    lifeProbe.watch(broker)
  }

  "A NetworkBroker" should {

    "manage the lifetime of the components" in {

      "instantiate all components in a graph when the network starts" in new BrokerInstance {
        lazy val probe = TestProbe()

        def instanceProps(id: String) = Props(new Actor {
          probe.ref ! s"started_$id"
          def receive = Actor.ignoringBehavior
        })

        lazy val graph = new TwoNodeGraph {
          behavior(n1, instanceProps("n1"))
          behavior(n2, instanceProps("n2"))
        }

        probe must receive.allOf("started_n1", "started_n2")
      }

      "terminate the network when all components terminate" in new BrokerInstance {

        lazy val instanceProps = Props(new Actor {
          context.stop(self)
          def receive = Actor.ignoringBehavior
        })

        lazy val graph = new TwoNodeGraph {
          behavior(n1, instanceProps)
          behavior(n2, instanceProps)
        }

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
      }
    }

    "handle initial values correctly" in {

      "forward initial values into components as Incoming messages" in new BrokerInstance {
        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
          initial("aaa") ~> (n1, 1)
        }

        graph.n1Probe must receive(Incoming("in1", "aaa"))
      }

      "fail if an initial value cannot be converted to the target type" in new BrokerInstance {
        lazy val graph = new SingleNodeGraph {
          val n1Probe = probeBehavior(n1)
          initial(4) ~> (n1, 1)
        }

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
        outputProbe must receive.like {
          case NetworkBroker.Error(msg) => msg mustEqual "Could not deserialize initial data for n1[in1]"
        }.afterOthers
      }
    }

    "handle data emitted by components correctly" in {

      "forward Outgoing messages to connected ports as Incoming messages" in new BrokerInstance {
        lazy val graph = new ChainGraph[String] {
          val outProbe = probeBehavior(outNode)
        }

        graph.outProbe must receive(Incoming("in1", "init")).afterOthers
      }

      "do type conversions between two ports if needed" in new BrokerInstance {
        case class MyData(n: Int, data: String)
        implicit lazy val jf = lift({ js: JsValue => MyData(1, js.asInstanceOf[JsString].value) })

        lazy val graph = new ChainGraph[MyData] {
          val outProbe = probeBehavior(outNode)
        }

        graph.outProbe must receive(Incoming("in1", MyData(1, "init"))).afterOthers
      }

      "fail if the outgoing data cannot be converted into the target type" in new BrokerInstance {
        case class MyData(n: Int, data: String)
        implicit lazy val jf = lift({ js: JsValue => MyData(js.asInstanceOf[JsNumber].value.intValue, "data") })

        lazy val graph = new ChainGraph[MyData]

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
        outputProbe must receive.like {
          case NetworkBroker.Error(msg) => msg mustEqual
            "Could not deserialize \"init\" (sent by n1[out]) to a format supported by n2[in1]"
        }.afterOthers
      }

      "fail if the outgoing data comes from an unknown actor" in new BrokerInstance {
        lazy val graph = new SingleNodeGraph

        broker ! Outgoing("out1", "data")

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
        outputProbe must receive.like {
          case NetworkBroker.Error(msg) => msg mustEqual "Internal runtime error"
        }.afterOthers
      }
    }

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
