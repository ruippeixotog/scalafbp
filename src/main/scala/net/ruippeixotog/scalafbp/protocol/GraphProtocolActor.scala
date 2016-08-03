package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.graph
import net.ruippeixotog.scalafbp.protocol.message.GraphMessage
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.runtime.LogicActor.GraphUpdated

class GraphProtocolActor(compRegistry: ComponentRegistry, logicActor: ActorRef)
    extends AbstractProtocolActor[GraphMessage] {

  var graphs = Map[String, graph.Graph]()

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def update(id: String)(f: graph.Graph => graph.Graph): Future[_] = {
    val oldGraph = graphs.getOrElse(id, graph.Graph(id))
    val newGraph = f(oldGraph)
    graphs += (id -> newGraph)
    logicActor ? GraphUpdated(id, newGraph)
  }

  def updateNode(id: String, nodeId: String)(f: graph.Node => graph.Node): Future[_] = {
    update(id) { old =>
      old.nodes.get(nodeId) match {
        case Some(node) => old.copy(nodes = old.nodes + (nodeId -> f(node)))
        case None =>
          log.warn(s"Tried to update a non-existing node: $nodeId")
          old
      }
    }
  }

  def updateConn(id: String, tgt: Edge)(f: graph.InConnection => graph.InConnection): Future[_] = {
    update(id) { old =>
      old.connections.get((tgt.node, tgt.port)) match {
        case Some(conn) => old.copy(connections = old.connections + ((tgt.node, tgt.port) -> f(conn)))
        case None =>
          log.warn(s"Tried to update a non-existing connection to $tgt")
          old
      }
    }
  }

  def returnPayload(pf: PartialFunction[GraphMessage, Future[Any]]): PartialFunction[GraphMessage, Unit] = {
    case msg if pf.isDefinedAt(msg) =>
      val replyTo = sender()
      pf(msg).foreach { _ => replyTo ! msg }
  }

  def receiveMessage = returnPayload {
    case payload: Clear =>
      val newGraph = graph.Graph(payload.id)
      graphs += (payload.id -> newGraph)
      logicActor ? GraphUpdated(payload.id, newGraph)

    case payload: AddNode =>
      compRegistry.get(payload.component) match {
        case Some(comp) =>
          update(payload.graph) { old =>
            old.copy(nodes = old.nodes + (payload.id ->
              graph.Node(comp, payload.metadata.getOrElse(Map()))))
          }

        case None =>
          log.warn(s"Unknown component: ${payload.component}")
          Future.failed(new NoSuchElementException)
      }

    case payload: RemoveNode =>
      update(payload.graph) { old =>
        old.copy(nodes = old.nodes - payload.id)
      }

    case payload: ChangeNode =>
      updateNode(payload.graph, payload.id) { old =>
        old.copy(metadata = payload.metadata)
      }

    case payload: AddEdge =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections + ((payload.tgt.node, payload.tgt.port) ->
          graph.Edge(payload.src.node, payload.src.port, payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveEdge =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections - ((payload.tgt.node, payload.tgt.port)))
      }

    case payload: ChangeEdge =>
      updateConn(payload.graph, payload.tgt) {
        case edge: graph.Edge => edge.copy(metadata = payload.metadata)
        case conn => conn
      }

    case payload: AddInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections + ((payload.tgt.node, payload.tgt.port) ->
          graph.IIP(payload.src.data, payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections - ((payload.tgt.node, payload.tgt.port)))
      }
  }
}
