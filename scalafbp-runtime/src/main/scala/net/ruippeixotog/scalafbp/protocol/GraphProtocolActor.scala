package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import spray.json.JsNull

import net.ruippeixotog.scalafbp.protocol.message.FromMessageConversions._
import net.ruippeixotog.scalafbp.protocol.message.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.runtime
import net.ruippeixotog.scalafbp.runtime.GraphStore.{ EdgeKey, GraphKey, InitialKey, NodeKey }
import net.ruippeixotog.scalafbp.runtime._

class GraphProtocolActor(compRegistry: ComponentRegistry, graphStore: ActorRef)
    extends AbstractProtocolActor[GraphMessage] {

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def returnReply(pf: PartialFunction[GraphMessage, Future[GraphMessage]]): PartialFunction[GraphMessage, Unit] = {
    case msg if pf.isDefinedAt(msg) =>
      val replyTo = sender()
      pf(msg).onComplete {
        case Success(reply) => replyTo ! reply
        case Failure(ex) =>
          log.warn(s"Invalid operation $msg: ${ex.getMessage}")
          replyTo ! Error(ex.getMessage)
      }
  }

  def askStore[A](req: GraphStore.Request[A]) = (graphStore ? req).flatMap {
    case GraphStore.Error(ex) => Future.failed(ex)
    case res => Future.successful(res)
  }

  def receiveMessage = returnReply {
    case payload: Clear =>
      val key = GraphKey(payload.id)
      val graph = runtime.Graph(payload.id)
      askStore(GraphStore.Create(key, graph)).map(_ => payload)

    case payload: AddNode =>
      compRegistry.get(payload.component) match {
        case Some(comp) =>
          val key = NodeKey(payload.graph, payload.id)
          val node = runtime.Node(comp, payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
          askStore(GraphStore.Create(key, node)).map(_ => payload)

        case None =>
          Future.failed(new NoSuchElementException(s"Unknown component ${payload.component}"))
      }

    case payload: RemoveNode =>
      val key = NodeKey(payload.graph, payload.id)
      askStore(GraphStore.Delete(key)).map(_ => payload)

    case payload: RenameNode =>
      val key = NodeKey(payload.graph, payload.from)
      askStore(GraphStore.Rename(key, payload.to)).map(_ => payload)

    case payload: ChangeNode =>
      val key = NodeKey(payload.graph, payload.id)
      def f(node: Node) = node.copy(metadata = (node.metadata ++ payload.metadata).filter(_._2 != JsNull))

      askStore(GraphStore.Update(key, f)).mapTo[GraphStore.Updated[Node]].map { res =>
        payload.copy(metadata = res.newEntity.metadata)
      }

    case payload: AddEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      val edge = runtime.Edge(payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
      askStore(GraphStore.Create(key, edge)).map(_ => payload)

    case payload: RemoveEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      askStore(GraphStore.Delete(key)).map(_ => payload)

    case payload: ChangeEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      def f(edge: runtime.Edge) = edge.copy(metadata = (edge.metadata ++ payload.metadata).filter(_._2 != JsNull))

      askStore(GraphStore.Update(key, f)).mapTo[GraphStore.Updated[runtime.Edge]].map { res =>
        payload.copy(metadata = res.newEntity.metadata)
      }

    case payload: AddInitial =>
      val key = InitialKey(payload.graph, payload.tgt.toPortRef)
      val initial = runtime.Initial(payload.src.data, payload.metadata.getOrElse(Map()).filter(_._2 != JsNull))
      askStore(GraphStore.Create(key, initial)).map(_ => payload)

    case payload: RemoveInitial =>
      val key = InitialKey(payload.graph, payload.tgt.toPortRef)
      askStore(GraphStore.Delete(key)).map(_ => payload)
  }
}
