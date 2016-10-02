package net.ruippeixotog.scalafbp.runtime

import akka.actor.{ Actor, Props, Terminated }

import net.ruippeixotog.scalafbp.component.Component
import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.runtime.NetworkBroker.External

case class GraphComponent(graph: Graph) extends Component {
  val name = "scalafbp/" + graph.id
  val description = s"Executes the graph ${graph.id} as a component"
  val icon = Some("sitemap")

  val inPorts = graph.publicIn.flatMap {
    case (public, PublicPort(ref, _)) => graph.inPortInfo(ref).map(_.withId(public))
  }.toList

  val outPorts = graph.publicOut.flatMap {
    case (public, PublicPort(ref, _)) => graph.outPortInfo(ref).map(_.withId(public))
  }.toList

  val instanceProps = Props(new Actor {
    val brokerActor = context.actorOf(NetworkBroker.props(graph, false, self, self))
    context.watch(brokerActor)

    def receive = {
      case msg: ComponentMessage => brokerActor ! External(msg)
      case External(msg) => context.parent ! msg

      case msg: Output => context.parent ! msg
      case msg: NetworkBroker.Error => context.parent ! msg
      case msg: NetworkBroker.ProcessError => throw new Exception(msg.msg)

      case Terminated(`brokerActor`) => context.stop(self)
    }
  })
}
