package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

class NetworkBrokerExternalsSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

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
  }
}
