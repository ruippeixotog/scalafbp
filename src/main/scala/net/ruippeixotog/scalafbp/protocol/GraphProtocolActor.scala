package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future

import spray.json.JsNull

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.message.FromMessageConversions._
import net.ruippeixotog.scalafbp.protocol.message.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.runtime
import net.ruippeixotog.scalafbp.runtime._

class GraphProtocolActor(compRegistry: ComponentRegistry, graphStore: GraphStore)
    extends AbstractProtocolActor[GraphMessage] {

  implicit val ec = context.dispatcher

  def returnReply(pf: PartialFunction[GraphMessage, Future[GraphMessage]]): PartialFunction[GraphMessage, Unit] = {
    case msg if pf.isDefinedAt(msg) =>
      val replyTo = sender()
      pf(msg).foreach(replyTo ! _)
  }

  def receiveMessage = returnReply {
    case payload: Clear =>
      graphStore.create(payload.id, runtime.Graph(payload.id)).map(_ => payload)

    case payload: AddNode =>
      compRegistry.get(payload.component) match {
        case Some(comp) =>
          graphStore.createNode(payload.graph, payload.id, runtime.Node(comp, payload.metadata.getOrElse(Map())))
            .map(_ => payload)

        case None =>
          log.warn(s"Unknown component: ${payload.component}")
          Future.failed(new NoSuchElementException)
      }

    case payload: RemoveNode => // TODO refactor this
      val delNode = graphStore.deleteNode(payload.graph, payload.id)
      val delEdges = delNode.flatMap { _ =>
        graphStore.allEdges(payload.graph).foldLeft(Future.successful(())) {
          case (fut, (src, tgt)) if src.node == payload.id || tgt.node == payload.id =>
            fut.flatMap { _ => graphStore.deleteEdge(payload.graph, src, tgt) }

          case (fut, _) => fut
        }
      }
      val delInitials = delEdges.flatMap { _ =>
        graphStore.allInitials(payload.graph).foldLeft(Future.successful(())) {
          case (fut, tgt) if tgt.node == payload.id =>
            fut.flatMap { _ => graphStore.deleteInitial(payload.graph, tgt) }

          case (fut, _) => fut
        }
      }
      delInitials.map(_ => payload)

    case payload: ChangeNode => // TODO refactor this
      graphStore.updateNode(payload.graph, payload.id) { node =>
        node.copy(metadata = (node.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }.map { _ =>
        val node = graphStore.getNode(payload.graph, payload.id).get
        payload.copy(metadata = (node.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }

    case payload: AddEdge =>
      graphStore.createEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef,
        runtime.Edge(payload.metadata.getOrElse(Map()))).map(_ => payload)

    case payload: RemoveEdge =>
      graphStore.deleteEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef).map(_ => payload)

    case payload: ChangeEdge =>
      graphStore.updateEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef) { edge =>
        edge.copy(metadata = (edge.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }.map { _ =>
        val edge = graphStore.getEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef).get
        payload.copy(metadata = (edge.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }

    case payload: AddInitial =>
      graphStore.createInitial(payload.graph, payload.tgt.toPortRef,
        runtime.Initial(payload.src.data, payload.metadata.getOrElse(Map())))
        .map(_ => payload)

    case payload: RemoveInitial =>
      graphStore.deleteInitial(payload.graph, payload.tgt.toPortRef).map(_ => payload)
  }
}
