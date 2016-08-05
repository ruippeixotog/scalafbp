package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.FromMessageConversions._
import net.ruippeixotog.scalafbp.protocol.message.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.runtime
import net.ruippeixotog.scalafbp.runtime._

class GraphProtocolActor(compRegistry: ComponentRegistry, graphStore: GraphStore)
    extends AbstractProtocolActor[GraphMessage] {

  implicit val ec = context.dispatcher

  def returnPayload(pf: PartialFunction[GraphMessage, Future[Any]]): PartialFunction[GraphMessage, Unit] = {
    case msg if pf.isDefinedAt(msg) =>
      val replyTo = sender()
      pf(msg).foreach { _ => replyTo ! msg }
  }

  def receiveMessage = returnPayload {
    case payload: Clear =>
      graphStore.create(payload.id, runtime.Graph(payload.id))

    case payload: AddNode =>
      compRegistry.get(payload.component) match {
        case Some(comp) =>
          graphStore.createNode(payload.graph, payload.id, runtime.Node(comp, payload.metadata.getOrElse(Map())))

        case None =>
          log.warn(s"Unknown component: ${payload.component}")
          Future.failed(new NoSuchElementException)
      }

    case payload: RemoveNode =>
      graphStore.deleteNode(payload.graph, payload.id)

    case payload: ChangeNode =>
      graphStore.updateNode(payload.graph, payload.id)(_.copy(metadata = payload.metadata))

    case payload: AddEdge =>
      graphStore.createConn(payload.graph, payload.tgt.toPortRef,
        runtime.Edge(payload.src.toPortRef, payload.metadata.getOrElse(Map())))

    case payload: RemoveEdge =>
      graphStore.deleteConn(payload.graph, payload.tgt.toPortRef)

    case payload: ChangeEdge =>
      graphStore.updateConn(payload.graph, payload.tgt.toPortRef) {
        case runtime.IIP(value, metadata) => runtime.IIP(value, payload.metadata)
        case runtime.Edge(src, metadata) => runtime.Edge(src, payload.metadata)
      }

    case payload: AddInitial =>
      graphStore.createConn(payload.graph, payload.tgt.toPortRef,
        runtime.IIP(payload.src.data, payload.metadata.getOrElse(Map())))

    case payload: RemoveInitial =>
      graphStore.deleteConn(payload.graph, payload.tgt.toPortRef)
  }
}
