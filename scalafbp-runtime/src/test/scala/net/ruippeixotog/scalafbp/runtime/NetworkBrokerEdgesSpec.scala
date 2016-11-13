package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.Terminated
import spray.json.DefaultJsonProtocol._
import spray.json.{ JsNumber, JsString, JsValue }

import net.ruippeixotog.scalafbp.component.ComponentActor.{ Incoming, Outgoing }

class NetworkBrokerEdgesSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "handle data emitted by components correctly" in {

      "forward Outgoing messages to connected ports as Incoming messages" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new ChainGraph[String] {
          val outProbe = probeBehavior(outNode)
        }

        graph.outProbe must receive(Incoming("in1", "init")).afterOthers
      }

      "do type conversions between two ports if needed" in new BrokerInstance {
        def _graph = graph

        case class MyData(n: Int, data: String)
        implicit lazy val jf = lift({ js: JsValue => MyData(1, js.asInstanceOf[JsString].value) })

        lazy val graph = new ChainGraph[MyData] {
          val outProbe = probeBehavior(outNode)
        }

        graph.outProbe must receive(Incoming("in1", MyData(1, "init"))).afterOthers
      }

      "fail if the outgoing data cannot be converted into the target type" in new BrokerInstance {
        def _graph = graph

        case class MyData(n: Int, data: String)
        implicit lazy val jf = lift({ js: JsValue => MyData(js.asInstanceOf[JsNumber].value.intValue, "data") })

        lazy val graph = new ChainGraph[MyData]

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
        outputProbe must receive.like {
          case NetworkBroker.NetworkError(msg) => msg mustEqual
            "Could not deserialize \"init\" (sent by n1[out]) to a format supported by n2[in1]"
        }.afterOthers
      }

      "fail if the outgoing data comes from an unknown actor" in new BrokerInstance {
        def _graph = graph

        lazy val graph = new SingleNodeGraph

        broker ! Outgoing("out1", "data")

        lifeProbe must receive.like { case Terminated(`broker`) => ok }
        outputProbe must receive.like {
          case NetworkBroker.NetworkError(msg) => msg mustEqual "Internal runtime error"
        }.afterOthers
      }
    }
  }
}
