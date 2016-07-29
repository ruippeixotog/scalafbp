package net.ruippeixotog.scalafbp.runtime

import akka.actor.Actor

import net.ruippeixotog.scalafbp.component
import net.ruippeixotog.scalafbp.component.ComponentRegistry
import net.ruippeixotog.scalafbp.protocol.GraphMessages._
import net.ruippeixotog.scalafbp.protocol.{ Graph => GraphProtocol }

class GraphProtocolActor extends Actor {
  var graphs = Map[String, component.Graph]()

  def wrap(payload: Payload) = GraphProtocol(payload)

  def update(graph: String)(f: component.Graph => component.Graph) =
    graphs += (graph -> f(graphs(graph)))

  def returnPayload(pf: Receive): Receive = {
    case msg: Payload if pf.isDefinedAt(msg) =>
      pf(msg)
      println(graphs)
      sender() ! wrap(msg)
  }

  def receive = returnPayload {
    case payload: Clear =>
      graphs += (payload.id -> component.Graph(payload.id))

    case payload: AddNode =>
      update(payload.graph) { old =>
        old.copy(nodes = old.nodes + (payload.id ->
          component.Node(ComponentRegistry.registry(payload.component), payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveNode =>
      update(payload.graph) { old =>
        old.copy(nodes = old.nodes - payload.id)
      }

    case payload: AddEdge =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections + ((payload.tgt.node, payload.tgt.port) ->
          component.Edge(payload.src.node, payload.src.port, payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveEdge =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections - ((payload.tgt.node, payload.tgt.port)))
      }

    case payload: AddInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections + ((payload.tgt.node, payload.tgt.port) ->
          component.IIP(payload.src.data, payload.metadata.getOrElse(Map()))))
      }

    case payload: RemoveInitial =>
      update(payload.graph) { old =>
        old.copy(connections = old.connections - ((payload.tgt.node, payload.tgt.port)))
      }

    case _: AddInPort | _: AddOutPort | _: ChangeNode => // not implemented

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
