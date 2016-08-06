package net.ruippeixotog.scalafbp.runtime

import scala.concurrent.{ ExecutionContext, Future }

import akka.event.slf4j.SLF4JLogging
import monocle.function.At.at
import monocle.function.all.index
import monocle.macros.GenLens
import monocle.std.map._
import monocle.{ Iso, Lens, Optional }

class GraphStore(implicit ec: ExecutionContext) extends SLF4JLogging {
  private type Store = Map[String, Graph]

  private[this] var graphs = Map[String, Graph]()

  private[this] def updateLock(f: Map[String, Graph] => Map[String, Graph]): Future[Unit] =
    Future.successful(synchronized(graphs = f(graphs))) // TODO improve this later to a better locking method

  private[this] def getOrElseIso[T](default: => T) = Iso[Option[T], T](_.getOrElse(default))(Some.apply)

  private[this] def graphLens(id: String): Lens[Store, Option[Graph]] = at(id)
  private[this] def graphLensOpt(id: String): Optional[Store, Graph] = index(id)

  private[this] def nodeLens(id: String, nodeId: String): Optional[Store, Option[Node]] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.nodes) ^|-> at(nodeId)

  private[this] def edgeLens(id: String, src: PortRef, tgt: PortRef): Optional[Store, Option[Edge]] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.edges) ^|-> at(src) ^<-> getOrElseIso(Map()) ^|-> at(tgt)

  private[this] def initialLens(id: String, tgt: PortRef): Optional[Store, Option[Initial]] =
    graphLensOpt(id) ^|-> GenLens[Graph](_.initials) ^|-> at(tgt)

  def get(id: String) = graphLens(id).get(graphs)
  def create(id: String, graph: Graph) = updateLock { graphLens(id).set(Some(graph)) }
  def update(id: String)(f: Graph => Graph) = updateLock { graphLens(id).modify(_.map(f)) }

  def getNode(id: String, nodeId: String) = nodeLens(id, nodeId).getOption(graphs).flatten
  def createNode(id: String, nodeId: String, node: Node) = updateLock { nodeLens(id, nodeId).set(Some(node)) }
  def updateNode(id: String, nodeId: String)(f: Node => Node) = updateLock { nodeLens(id, nodeId).modify(_.map(f)) }
  def deleteNode(id: String, nodeId: String) = updateLock { nodeLens(id, nodeId).set(None) }

  def getEdge(id: String, src: PortRef, tgt: PortRef) = edgeLens(id, src, tgt).getOption(graphs).flatten

  def createEdge(id: String, src: PortRef, tgt: PortRef, edge: Edge) =
    updateLock { edgeLens(id, src, tgt).set(Some(edge)) }

  def updateEdge(id: String, src: PortRef, tgt: PortRef)(f: Edge => Edge) =
    updateLock { edgeLens(id, src, tgt).modify(_.map(f)) }

  def deleteEdge(id: String, src: PortRef, tgt: PortRef) =
    updateLock { edgeLens(id, src, tgt).set(None) }

  def getInitial(id: String, tgt: PortRef) = initialLens(id, tgt).getOption(graphs).flatten

  def createInitial(id: String, tgt: PortRef, init: Initial) =
    updateLock { initialLens(id, tgt).set(Some(init)) }

  def updateInitial(id: String, tgt: PortRef)(f: Initial => Initial) =
    updateLock { initialLens(id, tgt).modify(_.map(f)) }

  def deleteInitial(id: String, tgt: PortRef) =
    updateLock { initialLens(id, tgt).set(None) }
}
