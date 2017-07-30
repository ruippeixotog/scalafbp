package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.{ List => ListComponents, _ }
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.ComponentRegistry._
import net.ruippeixotog.scalafbp.runtime.GraphStore.{ GraphKey, GraphsKey }
import net.ruippeixotog.scalafbp.runtime.Store._
import net.ruippeixotog.scalafbp.runtime.{ ComponentRegistry, Graph, GraphComponent, GraphStore }

class ComponentProtocolActor(compRegistry: ActorRef, graphStore: ActorRef)
    extends AbstractProtocolActor[ComponentMessage] {

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  class GraphListener extends Actor {

    object GraphStoreKey {
      def unapply(key: Key[_, _]): Option[String] = key match {
        case k: GraphStore.Key[_] => Some(k.graphId)
        case _ => None
      }
    }

    def upsertGraphComponent(graph: Graph) = {
      val comp = GraphComponent(graph)
      compRegistry ! Upsert(ComponentKey(comp.name), comp)
    }

    graphStore ! Watch(GraphStore.Domain.all, self)
    graphStore ! GetAll(GraphsKey)

    override def postStop() = {
      graphStore ! Unwatch(GraphStore.Domain.all, self)
    }

    def receive = {
      case GotAll(_, graphs: List[Graph] @unchecked) => graphs.foreach(upsertGraphComponent)
      case Got(_, Some(graph: Graph)) => upsertGraphComponent(graph)

      case Event(Created(_, graph: Graph)) =>
        upsertGraphComponent(graph)
        log.info(s"${graph.id} was made available as component ${GraphComponent.getId(graph)}")

      case Event(Deleted(_, graph: Graph)) =>
        compRegistry ! Delete(ComponentKey(GraphComponent.getId(graph)))

      case Event(Created(GraphStoreKey(graphId), _)) => graphStore ! Get(GraphKey(graphId))
      case Event(Updated(GraphStoreKey(graphId), _, _)) => graphStore ! Get(GraphKey(graphId))
      case Event(Deleted(GraphStoreKey(graphId), _)) => graphStore ! Get(GraphKey(graphId))
    }
  }

  class ComponentListener(inner: ActorRef) extends Actor {
    context.watch(inner)
    compRegistry ! Watch(ComponentRegistry.Domain.all, self)
    compRegistry ! GetAll(ComponentsKey)

    def sameExternalApi(oldComp: component.Component, newComp: component.Component) =
      oldComp.inPorts == newComp.inPorts && oldComp.outPorts == newComp.outPorts

    override def postStop() = {
      compRegistry ! Unwatch(ComponentRegistry.Domain.all, self)
    }

    def receive = {
      case GotAll(_, comps: List[component.Component] @unchecked) =>
        comps.foreach(inner ! _.toMessage)
        inner ! ComponentsReady(comps.size)

      case Event(Created(_, comp: component.Component)) =>
        inner ! comp.toMessage

      case Event(Updated(_, oldComp: component.Component, newComp: component.Component)) =>
        if (!sameExternalApi(oldComp, newComp)) inner ! newComp.toMessage

      case Terminated(`inner`) => context.stop(self)
    }
  }

  context.actorOf(Props(new GraphListener))

  def receiveMessage = {
    case _: ListComponents =>
      val replyTo = sender()
      context.actorOf(Props(new ComponentListener(replyTo)))
  }
}
