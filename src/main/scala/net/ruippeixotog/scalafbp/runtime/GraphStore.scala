package net.ruippeixotog.scalafbp.runtime

import scala.concurrent.{ ExecutionContext, Future }

import akka.event.slf4j.SLF4JLogging
import monocle.Lens
import monocle.function.At.at
import monocle.macros.GenLens
import monocle.std.map._
import monocle.std.option._

class GraphStore(implicit ec: ExecutionContext) extends SLF4JLogging {
  private[this] var graphs = Map[String, Graph]()

  private[this] def updateLock(f: Map[String, Graph] => Map[String, Graph]): Future[Unit] =
    Future(synchronized(graphs = f(graphs))) // TODO improve this later, e.g. by changing to an actor?

  private[this] def graphLens(id: String): Lens[Map[String, Graph], Option[Graph]] = at(id)

  private[this] def nodeLens(id: String, nodeId: String) =
    graphLens(id) ^<-? some ^|-> GenLens[Graph](_.nodes) ^|-> at(nodeId)

  private[this] def connLens(id: String, tgt: PortRef) =
    graphLens(id) ^<-? some ^|-> GenLens[Graph](_.connections) ^|-> at(tgt)

  def get(id: String) = graphLens(id).get(graphs)
  def create(id: String, graph: Graph) = updateLock { graphLens(id).set(Some(graph)) }
  def update(id: String)(f: Graph => Graph) = updateLock { graphLens(id).modify(_.map(f)) }

  def getNode(id: String, nodeId: String) = nodeLens(id, nodeId).getOption(graphs).flatten
  def createNode(id: String, nodeId: String, node: Node) = updateLock { nodeLens(id, nodeId).set(Some(node)) }
  def updateNode(id: String, nodeId: String)(f: Node => Node) = updateLock { nodeLens(id, nodeId).modify(_.map(f)) }
  def deleteNode(id: String, nodeId: String) = updateLock { nodeLens(id, nodeId).set(None) }

  def getConn(id: String, tgt: PortRef) = connLens(id, tgt).getOption(graphs).flatten
  def createConn(id: String, tgt: PortRef, conn: InConnection) = updateLock { connLens(id, tgt).set(Some(conn)) }
  def updateConn(id: String, tgt: PortRef)(f: InConnection => InConnection) = updateLock { connLens(id, tgt).modify(_.map(f)) }
  def deleteConn(id: String, tgt: PortRef) = updateLock { connLens(id, tgt).set(None) }
}
