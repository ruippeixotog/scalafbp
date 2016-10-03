package net.ruippeixotog.scalafbp.runtime

import scala.util.{ Failure, Success, Try }

import akka.actor.{ Actor, ActorRef }
import akka.event.slf4j.SLF4JLogging
import monocle.Optional

import net.ruippeixotog.scalafbp.runtime.Store._

class Store[S](initial: S) extends Actor with SLF4JLogging {
  private[this] var store: S = initial
  private[this] var listeners = Map[String, Set[ActorRef]]().withDefaultValue(Set())

  def handleRequest[A](req: Request[S, A], store: S): (Response[S, A], S) = {
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
        if (from.domain != to.domain) throw new IllegalArgumentException(s"Can't move $from to outside ${from.domain}")
        (Renamed(from, to), from.rename(to, entity)(store))

      case (Rename(from, _), _) => throw new NoSuchElementException(s"Can't rename nonexistent $from")

      case (Delete(key), Some(Some(entity))) => (Deleted(key, entity), key.lens.set(None)(store))
      case (Delete(key), _) => throw new NoSuchElementException(s"Can't delete nonexistent $key")
    }
  }

  def receive = {
    case req: Request[S, a] =>
      Try(handleRequest(req, store)) match {
        case Success((res, newGraphs)) =>
          store = newGraphs
          listeners(req.key.domain).foreach(_ ! Event(res))
          sender() ! res

        case Failure(ex) =>
          sender() ! Error(ex)
      }

    case Watch(id, listener) => listeners += id -> (listeners(id) + listener)
    case Unwatch(id, listener) => listeners += id -> (listeners(id) - listener)
  }
}

object Store {
  trait Key[S, A] {
    def domain: String
    def lens: Optional[S, Option[A]]
  }

  trait RenamableKey[S, A] extends Key[S, A] {
    def rename(to: Key[S, A], curr: A): S => S =
      lens.set(None).andThen(to.lens.set(Some(curr)))
  }

  sealed trait Request[S, A] {
    def key: Key[S, A]
  }

  case class Get[S, A](key: Key[S, A]) extends Request[S, A]
  case class Create[S, A](key: Key[S, A], entity: A) extends Request[S, A]
  case class Update[S, A](key: Key[S, A], f: A => A) extends Request[S, A]
  case class Upsert[S, A](key: Key[S, A], entity: A) extends Request[S, A]
  case class Rename[S, A](key: RenamableKey[S, A], to: Key[S, A]) extends Request[S, A]
  case class Delete[S, A](key: Key[S, A]) extends Request[S, A]

  sealed trait Response[S, A]
  case class Got[S, A](key: Key[S, A], entity: Option[A]) extends Response[S, A]
  case class Created[S, A](key: Key[S, A], entity: A) extends Response[S, A]
  case class Updated[S, A](key: Key[S, A], oldEntity: A, newEntity: A) extends Response[S, A]
  case class Renamed[S, A](key: RenamableKey[S, A], to: Key[S, A]) extends Response[S, A]
  case class Deleted[S, A](key: Key[S, A], entity: A) extends Response[S, A]
  case class Error[S, A](ex: Throwable) extends Response[S, A]

  case class Watch(domain: String, listener: ActorRef)
  case class Unwatch(domain: String, listener: ActorRef)
  case class Event[S, A](res: Response[S, A])
}
