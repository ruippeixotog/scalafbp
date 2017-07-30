package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import spray.json.{ JsNull, JsValue }

import net.ruippeixotog.scalafbp.component.Component
import net.ruippeixotog.scalafbp.protocol.message.FromMessageConversions._
import net.ruippeixotog.scalafbp.protocol.message.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.runtime
import net.ruippeixotog.scalafbp.runtime.ComponentRegistry.ComponentKey
import net.ruippeixotog.scalafbp.runtime.GraphStore._
import net.ruippeixotog.scalafbp.runtime._

class GraphProtocolActor(compRegistry: ActorRef, graphStore: ActorRef)
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

  def processMeta(metadata: Map[String, JsValue]) = metadata.filter(_._2 != JsNull)
  def processMetaOpt(metadata: Option[Map[String, JsValue]]) = metadata.fold(Map.empty[String, JsValue])(processMeta)

  def askStore[A](req: GraphStore.Request[A]) = (graphStore ? req).flatMap {
    case Store.Error(ex) => Future.failed(ex)
    case res => Future.successful(res)
  }

  def receiveMessage = returnReply {
    case payload: Clear =>
      val key = GraphKey(payload.id)
      val graph = runtime.Graph(payload.id)
      askStore(Store.Upsert(key, graph)).map(_ => payload)

    case payload: AddNode =>
      val compKey = ComponentKey(payload.component)
      (compRegistry ? Store.Get(compKey)).mapTo[Store.Got[_, Component]].flatMap {

        case Store.Got(_, Some(comp)) =>
          val key = NodeKey(payload.graph, payload.id)
          val node = runtime.Node(comp, processMetaOpt(payload.metadata))
          askStore(Store.Upsert(key, node)).map(_ => payload)

        case Store.Got(_, None) =>
          Future.failed(new NoSuchElementException(s"Unknown component ${payload.component}"))
      }

    case payload: RemoveNode =>
      val key = NodeKey(payload.graph, payload.id)
      askStore(Store.Delete(key)).map(_ => payload)

    case payload: RenameNode =>
      val key = NodeKey(payload.graph, payload.from)
      askStore(Store.Rename(key, NodeKey(payload.graph, payload.to))).map(_ => payload)

    case payload: ChangeNode =>
      val key = NodeKey(payload.graph, payload.id)
      def f(node: Node) = node.copy(metadata = processMeta(node.metadata ++ payload.metadata))

      askStore(Store.Update(key, f)).mapTo[Store.Updated[_, Node]].map { res =>
        payload.copy(metadata = res.newEntity.metadata)
      }

    case payload: AddEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      val edge = runtime.Edge(processMetaOpt(payload.metadata))
      askStore(Store.Upsert(key, edge)).map(_ => payload)

    case payload: RemoveEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      askStore(Store.Delete(key)).map(_ => payload)

    case payload: ChangeEdge =>
      val key = EdgeKey(payload.graph, payload.src.toPortRef, payload.tgt.toPortRef)
      def f(edge: runtime.Edge) = edge.copy(metadata = processMeta(edge.metadata ++ payload.metadata))

      askStore(Store.Update(key, f)).mapTo[Store.Updated[_, runtime.Edge]].map { res =>
        payload.copy(metadata = res.newEntity.metadata)
      }

    case payload: AddInitial =>
      val key = InitialKey(payload.graph, payload.tgt.toPortRef)
      val initial = runtime.Initial(payload.src.data, processMetaOpt(payload.metadata))
      askStore(Store.Upsert(key, initial)).map(_ => payload)

    case payload: RemoveInitial =>
      val key = InitialKey(payload.graph, payload.tgt.toPortRef)
      askStore(Store.Delete(key)).map(_ => payload)

    case payload: AddInPort =>
      val key = PublicInPortKey(payload.graph, payload.public)
      val port = runtime.PublicPort(PortRef(payload.node, payload.port), processMetaOpt(payload.metadata))
      askStore(Store.Upsert(key, port)).map(_ => payload)

    case payload: RemoveInPort =>
      val key = PublicInPortKey(payload.graph, payload.public)
      askStore(Store.Delete(key)).map(_ => payload)

    case payload: RenameInPort =>
      val key = PublicInPortKey(payload.graph, payload.from)
      askStore(Store.Rename(key, PublicInPortKey(payload.graph, payload.to))).map(_ => payload)

    case payload: AddOutPort =>
      val key = PublicOutPortKey(payload.graph, payload.public)
      val port = runtime.PublicPort(PortRef(payload.node, payload.port), processMetaOpt(payload.metadata))
      askStore(Store.Upsert(key, port)).map(_ => payload)

    case payload: RemoveOutPort =>
      val key = PublicOutPortKey(payload.graph, payload.public)
      askStore(Store.Delete(key)).map(_ => payload)

    case payload: RenameOutPort =>
      val key = PublicOutPortKey(payload.graph, payload.from)
      askStore(Store.Rename(key, PublicOutPortKey(payload.graph, payload.to))).map(_ => payload)
  }
}
