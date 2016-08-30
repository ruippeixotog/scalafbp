package net.ruippeixotog.scalafbp.runtime

import scala.concurrent.{ ExecutionContext, Future }

import akka.event.slf4j.SLF4JLogging
import monocle.function.At.at
import monocle.function.all._
import monocle.macros.GenLens
import monocle.std.map._
import monocle.{ Iso, Lens, Optional }
import monocle.Traversal

class GraphStore(implicit ec: ExecutionContext) extends SLF4JLogging {
  private type Store = Map[String, Graph]

  private[this] var graphs = Map[String, Graph]()

  private[this] def updateLock(f: Map[String, Graph] => Option[Map[String, Graph]]): Future[Unit] = {
    synchronized { // TODO improve this later to a better locking method
      f(graphs) match {
        case Some(newGraphs) => Future.successful(graphs = newGraphs)
        case None => Future.failed(new NoSuchElementException("Unknown graph or node"))
      }
    }
  }

  private[this] def getOrElseIso[T](default: => T) = Iso[Option[T], T](_.getOrElse(default))(Some.apply)

  private[this] def graphLens(id: String): Lens[Store, Option[Graph]] = at(id)

  private[this] def graphLensOpt(id: String): Optional[Store, Graph] = index(id)

  private[this] def nodeLens(id: String, nodeId: String): Optional[Store, Option[Node]] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.nodes) ^|-> at(nodeId)

  private[this] def nodeLensOpt(id: String, nodeId: String): Optional[Store, Node] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.nodes) ^|-? index(nodeId)

  private[this] def edgeLens(id: String, src: PortRef, tgt: PortRef): Optional[Store, Option[Edge]] =
    nodeLensOpt(id, src.node) ^|-> GenLens[Node](_.edges) ^|-> at(src.port) ^<-> getOrElseIso(Map()) ^|-> at(tgt)

  private[this] def initialLens(id: String, tgt: PortRef): Optional[Store, Option[Initial]] =
    nodeLensOpt(id, tgt.node) ^|-> GenLens[Node](_.initials) ^|-> at(tgt.port)

  private[this] def revEdgesLens(id: String): Traversal[Store, Map[PortRef, Edge]] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.nodes) ^|->> each ^|-> GenLens[Node](_.edges) ^|->> each

  def get(id: String) =
    graphLens(id).get(graphs)

  def create(id: String, graph: Graph) =
    updateLock { graphLens(id).set(Some(graph)).andThen(Some.apply) }

  def update(id: String)(f: Graph => Graph) =
    updateLock { graphLens(id).modify(_.map(f)).andThen(Some.apply) }

  def getNode(id: String, nodeId: String) =
    nodeLens(id, nodeId).getOption(graphs)

  def createNode(id: String, nodeId: String, node: Node) =
    updateLock { nodeLens(id, nodeId).setOption(Some(node)) }

  def renameNode(id: String, fromId: String, toId: String) =
    updateLock { graphs =>
      nodeLens(id, fromId).getOption(graphs).flatten.map { node =>
        nodeLens(id, toId).set(Some(node))
          .andThen(nodeLens(id, fromId).set(None))
          .andThen(revEdgesLens(id).modify(_.map {
            case (tgt, e) => (if (tgt.node == fromId) tgt.copy(node = toId) else tgt, e)
          }))(graphs)
      }
    }

  def updateNode(id: String, nodeId: String)(f: Node => Node) =
    updateLock { nodeLens(id, nodeId).modifyOption(_.map(f)) }

  def deleteNode(id: String, nodeId: String) =
    updateLock { graphs =>
      nodeLens(id, nodeId).setOption(None)(graphs).map {
        revEdgesLens(id).modify(_.filterKeys(_.node != nodeId))
      }
    }

  def getEdge(id: String, src: PortRef, tgt: PortRef) =
    edgeLens(id, src, tgt).getOption(graphs)

  def createEdge(id: String, src: PortRef, tgt: PortRef, edge: Edge) =
    updateLock { edgeLens(id, src, tgt).setOption(Some(edge)) }

  def updateEdge(id: String, src: PortRef, tgt: PortRef)(f: Edge => Edge) =
    updateLock { edgeLens(id, src, tgt).modifyOption(_.map(f)) }

  def deleteEdge(id: String, src: PortRef, tgt: PortRef) =
    updateLock { edgeLens(id, src, tgt).setOption(None) }

  def getInitial(id: String, tgt: PortRef) =
    initialLens(id, tgt).getOption(graphs)

  def createInitial(id: String, tgt: PortRef, init: Initial) =
    updateLock { initialLens(id, tgt).setOption(Some(init)) }

  def updateInitial(id: String, tgt: PortRef)(f: Initial => Initial) =
    updateLock { initialLens(id, tgt).modifyOption(_.map(f)) }

  def deleteInitial(id: String, tgt: PortRef) =
    updateLock { initialLens(id, tgt).setOption(None) }
}
