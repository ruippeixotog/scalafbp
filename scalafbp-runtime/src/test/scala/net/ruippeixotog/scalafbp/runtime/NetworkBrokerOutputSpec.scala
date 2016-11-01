package net.ruippeixotog.scalafbp.runtime

import scala.language.reflectiveCalls

class NetworkBrokerOutputSpec extends NetworkBrokerSpec {

  "A NetworkBroker" should {

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
