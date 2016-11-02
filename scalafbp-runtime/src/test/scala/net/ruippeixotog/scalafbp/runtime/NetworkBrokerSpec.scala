package net.ruippeixotog.scalafbp.runtime

import akka.actor.Props
import akka.testkit.TestProbe
import org.specs2.specification.Scope
import spray.json.DefaultJsonProtocol._

import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import net.ruippeixotog.scalafbp.component.PortDataMarshaller
import net.ruippeixotog.scalafbp.component.core.Repeat

abstract class NetworkBrokerSpec extends AkkaSpecification {

  class SingleNodeGraph extends GraphTemplate {
    val n1 = node[String](1, 1)
  }

  class TwoNodeGraph extends GraphTemplate {
    val n1, n2 = node[String](1, 1)
  }

  class ThreeNodeGraph extends GraphTemplate {
    val n1, n2, n3 = node[String](1, 1)
  }

  class ChainGraph[A: PortDataMarshaller] extends GraphTemplate {
    val inNode = node(Repeat)
    val outNode = node[A](1, 1)
    initial("init") ~> (inNode, "in")
    (inNode, "out") ~> (outNode, 1)
  }

  class TwoToTwoGraph extends GraphTemplate {
    val inNode1, inNode2, outNode1, outNode2 = node[String](1, 1)
    (inNode1, 1) ~> (outNode1, 1) <~ (inNode2, 1)
    (inNode1, 1) ~> (outNode2, 1) <~ (inNode2, 1)
  }

  abstract class BrokerInstance(dynamic: Boolean = false) extends Scope {
    def graph: GraphTemplate
    def externalProbe: TestProbe = null

    val lifeProbe, outputProbe = TestProbe()
    val broker = system.actorOf(Props(
      new NetworkBroker(graph, dynamic, outputProbe.ref, Option(externalProbe).map(_.ref))))

    lifeProbe.watch(broker)
  }
}
