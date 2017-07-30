package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

import akka.actor.{ Actor, Props, Terminated }
import akka.testkit.TestProbe

class NetworkBrokerLifetimeSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

    "manage the lifetime of the components" in {

      "instantiate all components in a graph when the network starts" in new BrokerInstance {
        def _graph = graph

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
        def _graph = graph

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
  }
}
