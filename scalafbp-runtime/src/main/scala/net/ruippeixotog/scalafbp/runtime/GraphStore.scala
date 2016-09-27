package net.ruippeixotog.scalafbp.runtime

import scala.util.{ Failure, Success, Try }

import akka.actor.{ Actor, ActorRef }
import akka.event.slf4j.SLF4JLogging
import monocle.function.At.at
import monocle.function.all._
import monocle.macros.GenLens
import monocle.std.map._
import monocle.{ Iso, Lens, Optional, Traversal }

import net.ruippeixotog.scalafbp.runtime.GraphStore._

class GraphStore extends Actor with SLF4JLogging {
  private[this] var store: Store = Map[String, Graph]()
  private[this] var listeners = Map[String, Set[ActorRef]]().withDefaultValue(Set())

  def handleRequest[A](req: Request[A], store: Store): (Response[A], Store) = {
    (req, req.key.lens.getOption(store)) match {
      case (Get(key), Some(entityOpt)) => (Got(key, entityOpt), store)
      case (Get(key), None) => throw new NoSuchElementException(s"Can't retrieve $key on a nonexistent path")

      case (Create(key, entity), Some(None)) => (Created(key, entity), key.lens.set(Some(entity))(store))
      case (Create(key, _), Some(Some(_))) => throw new IllegalArgumentException(s"$key already exists")
      case (Create(key, _), None) => throw new NoSuchElementException(s"Can't create $key on a nonexistent path")

      case (Update(key, f), Some(Some(oldEntity))) =>
        val newEntity = f(oldEntity)
        (Updated(key, oldEntity, newEntity), key.lens.set(Some(newEntity))(store))

      case (Update(key, _), _) => throw new NoSuchElementException(s"Can't update nonexistent $key")

      case (Upsert(key, entity), Some(None)) => (Created(key, entity), key.lens.set(Some(entity))(store))
      case (Upsert(key, entity), Some(Some(oldEntity))) =>
        (Updated(key, oldEntity, entity), key.lens.set(Some(entity))(store))

      case (Upsert(key, _), None) =>
        throw new NoSuchElementException(s"Can't create or update $key on a nonexistent path")

      case (Rename(from, to), Some(Some(entity))) =>
        if (from.graphId != to.graphId) throw new IllegalArgumentException(s"Can't move $from to another graph")
        (Renamed(from, to), from.rename(to, entity)(store))

      case (Rename(from, _), _) => throw new NoSuchElementException(s"Can't rename nonexistent $from")

      case (Delete(key), Some(Some(entity))) => (Deleted(key, entity), key.lens.set(None)(store))
      case (Delete(key), _) => throw new NoSuchElementException(s"Can't delete nonexistent $key")
    }
  }

  def receive = {
    case req: Request[a] =>
      Try(handleRequest(req, store)) match {
        case Success((res, newGraphs)) =>
          store = newGraphs
          listeners(req.key.graphId).foreach(_ ! Event(res))
          sender() ! res

        case Failure(ex) =>
          sender() ! Error(ex)
      }

    case Watch(id, listener) => listeners += id -> (listeners(id) + listener)
    case Unwatch(id, listener) => listeners += id -> (listeners(id) - listener)
  }
}

object GraphStore {
  type Store = Map[String, Graph]

  private def getOrElseIso[T](default: => T) = Iso[Option[T], T](_.getOrElse(default))(Some.apply)

  private def graphLens(graphId: String): Lens[Store, Option[Graph]] = at(graphId)

  private def graphLensOpt(graphId: String): Optional[Store, Graph] = index(graphId)

  private def nodeLens(graphId: String, nodeId: String): Optional[Store, Option[Node]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|-> at(nodeId)

  private def nodeLensOpt(graphId: String, nodeId: String): Optional[Store, Node] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|-? index(nodeId)

  private def edgeLens(graphId: String, src: PortRef, tgt: PortRef): Optional[Store, Option[Edge]] =
    nodeLensOpt(graphId, src.node) ^|-> GenLens[Node](_.edges) ^|-> at(src.port) ^<-> getOrElseIso(Map()) ^|-> at(tgt)

  private def initialLens(graphId: String, tgt: PortRef): Optional[Store, Option[Initial]] =
    nodeLensOpt(graphId, tgt.node) ^|-> GenLens[Node](_.initials) ^|-> at(tgt.port)

  private def publicInPortLens(graphId: String, publicId: String): Optional[Store, Option[PublicPort]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.publicIn) ^|-> at(publicId)

  private def publicOutPortLens(graphId: String, publicId: String): Optional[Store, Option[PublicPort]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.publicOut) ^|-> at(publicId)

  private def revEdgesLens(graphId: String): Traversal[Store, Map[PortRef, Edge]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|->> each ^|-> GenLens[Node](_.edges) ^|->> each

  sealed trait Key[A] {
    def graphId: String
    def lens: Optional[Store, Option[A]]
  }

  sealed trait RenamableKey[A] extends Key[A] {
    def rename(to: Key[A], curr: A): Store => Store =
      lens.set(None).andThen(to.lens.set(Some(curr)))
  }

  case class GraphKey(graphId: String) extends Key[Graph] {
    val lens = graphLens(graphId).asOptional
  }

  case class NodeKey(graphId: String, nodeId: String) extends RenamableKey[Node] {
    val lens = nodeLens(graphId, nodeId)

    override def rename(to: Key[Node], curr: Node) =
      super.rename(to, curr).andThen(revEdgesLens(graphId).modify(_.map {
        case (tgt, e) => (if (tgt.node == nodeId) tgt.copy(node = to.asInstanceOf[NodeKey].nodeId) else tgt, e)
      }))
  }

  case class EdgeKey(graphId: String, src: PortRef, tgt: PortRef) extends Key[Edge] {
    val lens = edgeLens(graphId, src, tgt)
  }

  case class InitialKey(graphId: String, tgt: PortRef) extends Key[Initial] {
    val lens = initialLens(graphId, tgt)
  }

  case class PublicInPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = publicInPortLens(graphId, publicId)
  }

  case class PublicOutPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = publicOutPortLens(graphId, publicId)
  }

  sealed trait Request[A] {
    def key: Key[A]
  }

  case class Get[A](key: Key[A]) extends Request[A]
  case class Create[A](key: Key[A], entity: A) extends Request[A]
  case class Update[A](key: Key[A], f: A => A) extends Request[A]
  case class Upsert[A](key: Key[A], entity: A) extends Request[A]
  case class Rename[A](key: RenamableKey[A], to: Key[A]) extends Request[A]
  case class Delete[A](key: Key[A]) extends Request[A]

  sealed trait Response[A]
  case class Got[A](key: Key[A], entity: Option[A]) extends Response[A]
  case class Created[A](key: Key[A], entity: A) extends Response[A]
  case class Updated[A](key: Key[A], oldEntity: A, newEntity: A) extends Response[A]
  case class Renamed[A](key: RenamableKey[A], to: Key[A]) extends Response[A]
  case class Deleted[A](key: Key[A], entity: A) extends Response[A]
  case class Error[A](ex: Throwable) extends Response[A]

  case class Watch(id: String, listener: ActorRef)
  case class Unwatch(id: String, listener: ActorRef)
  case class Event[A](res: Response[A])
}
