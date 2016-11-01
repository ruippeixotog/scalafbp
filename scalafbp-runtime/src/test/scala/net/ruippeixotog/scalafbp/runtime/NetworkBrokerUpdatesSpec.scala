package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

class NetworkBrokerUpdatesSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

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
  }
}
