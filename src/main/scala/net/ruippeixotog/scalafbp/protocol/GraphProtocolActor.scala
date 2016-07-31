package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef }
import akka.pattern.ask
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.graph
import net.ruippeixotog.scalafbp.protocol.message.GraphMessages._
import net.ruippeixotog.scalafbp.protocol.message.{ Graph => GraphProtocol }
import net.ruippeixotog.scalafbp.runtime.LogicActor.GraphUpdated

class GraphProtocolActor(logicActor: ActorRef) extends Actor {
  var graphs = Map[String, graph.Graph]()

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def wrap(payload: Payload) = GraphProtocol(payload)

  def update(id: String)(f: graph.Graph => graph.Graph): Future[_] = {
    val oldGraph = graphs.getOrElse(id, graph.Graph(id))
    val newGraph = f(oldGraph)
    graphs += (id -> newGraph)
    logicActor ? GraphUpdated(id, newGraph)
  }

  def returnPayload(pf: PartialFunction[Payload, Future[Any]]): Receive = {
    case msg: Payload if pf.isDefinedAt(msg) =>
      val replyTo = sender()
      pf(msg).foreach { _ => replyTo ! wrap(msg) }
  }

  def receive = returnPayload {
    case payload: Clear =>
      val newGraph = graph.Graph(payload.id)
      graphs += (payload.id -> newGraph)
      logicActor ? GraphUpdated(payload.id, newGraph)

    case payload: AddNode =>
      update(payload.graph) { old =>
        old.copy(nodes = old.nodes + (payload.id ->
          graph.Node(ComponentRegistry.registry(payload.component), payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveNode =>
      update(payload.graph) { old =>
        old.copy(nodes = old.nodes - payload.id)
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

    case payload: AddInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections + ((payload.tgt.node, payload.tgt.port) ->
          graph.IIP(payload.src.data, payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections - ((payload.tgt.node, payload.tgt.port)))
      }

    case _: AddInPort | _: AddOutPort | _: ChangeNode => // not implemented
      Future.successful(())

    case msg =>
      println(s"UNHANDLED MESSAGE: $msg")
      Future.failed(new Exception)
  }
}
