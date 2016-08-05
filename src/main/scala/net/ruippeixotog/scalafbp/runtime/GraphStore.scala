package net.ruippeixotog.scalafbp.runtime

import scala.concurrent.{ ExecutionContext, Future }

import akka.event.slf4j.SLF4JLogging

import net.ruippeixotog.scalafbp.runtime

class GraphStore(implicit ec: ExecutionContext) extends SLF4JLogging {
  private[this] var graphs = Map[String, Graph]()

  private[this] def withLock[T](block: => T): Future[T] =
    Future(graphs.synchronized(block)) // TODO improve this later, e.g. by changing to an actor?

  def get(id: String) = graphs.get(id)

  def create(id: String, graph: Graph)(implicit ec: ExecutionContext): Future[Unit] = withLock {
    graphs += id -> graph
  }

  def update(id: String)(f: Graph => Graph): Future[Unit] = withLock {
    val oldGraph = graphs.getOrElse(id, runtime.Graph(id))
    graphs += id -> f(oldGraph)
  }

  def getNode(id: String, nodeId: String) = graphs.get(id).flatMap(_.nodes.get(nodeId))

  def createNode(id: String, nodeId: String, node: Node): Future[Unit] = update(id) { old =>
    old.copy(nodes = old.nodes + (nodeId -> node))
  }

  def updateOrDeleteNode(id: String, nodeId: String)(f: Node => Option[Node]) = update(id) { old =>
    old.nodes.get(nodeId) match {
      case Some(node) =>
        f(node) match {
          case Some(newNode) => old.copy(nodes = old.nodes + (nodeId -> newNode))
          case None => old.copy(nodes = old.nodes - nodeId)
        }
      case None =>
        log.warn(s"Tried to update a non-existing node: $nodeId")
        old
    }
  }

  def updateNode(id: String, nodeId: String)(f: Node => Node) = updateOrDeleteNode(id, nodeId)(f.andThen(Some.apply))
  def deleteNode(id: String, nodeId: String) = updateOrDeleteNode(id, nodeId)(_ => None)

  def getConn(id: String, tgt: PortRef) = graphs.get(id).flatMap(_.connections.get(tgt))

  def createConn(id: String, tgt: PortRef, conn: InConnection): Future[Unit] = update(id) { old =>
    old.copy(connections = old.connections + (tgt -> conn))
  }

  def updateOrDeleteConn(id: String, tgt: PortRef)(f: InConnection => Option[InConnection]) = update(id) { old =>
    old.connections.get(tgt) match {
      case Some(conn) =>
        f(conn) match {
          case Some(newConn) => old.copy(connections = old.connections + (tgt -> newConn))
          case None => old.copy(connections = old.connections - tgt)
        }
      case None =>
        log.warn(s"Tried to update a non-existing connection to $tgt")
        old
    }
  }

  def updateConn(id: String, tgt: PortRef)(f: InConnection => InConnection) =
    updateOrDeleteConn(id, tgt)(f.andThen(Some.apply))

  def deleteConn(id: String, tgt: PortRef) = updateOrDeleteConn(id, tgt)(_ => None)
}
