package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.Terminated
import spray.json.DefaultJsonProtocol._

import net.ruippeixotog.scalafbp.component.ComponentActor.Incoming

class NetworkBrokerInitialsSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

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
  }
}
