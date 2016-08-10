package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future
import scala.util.{ Failure, Success }

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
      pf(msg).onComplete {
        case Success(reply) => replyTo ! reply
        case Failure(ex) => log.warn(s"Invalid operation $msg: ${ex.getMessage}")
      }
  }

  def receiveMessage = returnReply {
    case payload: Clear =>
      graphStore.create(payload.id, runtime.Graph(payload.id)).map(_ => payload)

    case payload: AddNode =>
      compRegistry.get(payload.component) match {
        case Some(comp) =>
          val newNode = runtime.Node(comp, payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
          graphStore.createNode(payload.graph, payload.id, newNode)
            .map(_ => payload)

        case None =>
          Future.failed(new NoSuchElementException(s"unknown component ${payload.component}"))
      }

    case payload: RemoveNode =>
      graphStore.deleteNode(payload.graph, payload.id).map(_ => payload)

    case payload: RenameNode =>
      graphStore.renameNode(payload.graph, payload.from, payload.to).map(_ => payload)

    case payload: ChangeNode =>
      graphStore.updateNode(payload.graph, payload.id) { node =>
        node.copy(metadata = (node.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }.map { _ =>
        val node = graphStore.getNode(payload.graph, payload.id).get.get
        payload.copy(metadata = node.metadata)
      }

    case payload: AddEdge =>
      val newEdge = runtime.Edge(payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
      graphStore.createEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef, newEdge).map(_ => payload)

    case payload: RemoveEdge =>
      graphStore.deleteEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef).map(_ => payload)

    case payload: ChangeEdge =>
      graphStore.updateEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef) { edge =>
        edge.copy(metadata = (edge.metadata ++ payload.metadata).filter(_._2 != JsNull))
      }.map { _ =>
        val edge = graphStore.getEdge(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef).flatten.get
        payload.copy(metadata = edge.metadata)
      }

    case payload: AddInitial =>
      val newInitial = runtime.Initial(payload.src.data, payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
      graphStore.createInitial(payload.graph, payload.tgt.toPortRef, newInitial).map(_ => payload)

    case payload: RemoveInitial =>
      graphStore.deleteInitial(payload.graph, payload.tgt.toPortRef).map(_ => payload)
  }
}
