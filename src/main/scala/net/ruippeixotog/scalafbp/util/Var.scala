package net.ruippeixotog.scalafbp.util

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom

sealed abstract class Var[+A] {
  def get: Option[A]

  final def map[B](f: A => B): Var[B] =
    new DepVar[B, Unit]((), _ => get.map(f), _ => List(this))

  final def flatMap[B](f: A => Var[B]): Var[B] =
    new DepVar[B, Option[Var[B]]](get.map(f), _.flatMap(_.get), List(this) ++ _)

  final def flatten[B](implicit ev: Option[A] <:< Option[Var[B]]): Var[B] =
    new DepVar[B, Option[Var[B]]](get, _.flatMap(_.get), List(this) ++ _)

  final def foreach[U](f: A => U): Unit = {
    dependentActions :+= f
    get.foreach(f)
  }

  final def zip[B](v: Var[B]): Var[(A, B)] =
    new DepVar[(A, B), Unit]((), _ => for (a1 <- get; a2 <- v.get) yield (a1, a2), _ => List(this, v))

  final def unzip[A1, A2](implicit ev: A => (A1, A2)): (Var[A1], Var[A2]) =
    (map(_._1), map(_._2))

  protected[util] var dependentVars: Set[DepVar[_, _]] = Set.empty
  protected[this] var dependentActions: Seq[A => Any] = Vector.empty
}

object Var {
  def undefined[A](): Source[A] = new Source(None)
  def apply[A](a: A): Source[A] = new Source(Some(a))
  def constant[A](a: A): Var[A] = new Source(Some(a))

  def sequence[A, M[X] <: SeqLike[X, M[X]]](in: M[Var[A]])(
    implicit
    cbf: CanBuildFrom[M[Var[A]], A, M[A]],
    cbf2: CanBuildFrom[M[A], A, M[A]]): Var[M[A]] = {

    in.foldRight(Var.constant(cbf(in).result())) { (v, acc) =>
      for (builder <- acc; elem <- v) yield elem +: builder
    }
  }
}

final class Source[A] private[util] (private[this] var value: Option[A]) extends Var[A] {
  def get = value

  def set(a: A): Unit = {
    value = Some(a)
    dependentActions.foreach(_(a))
    dependentVars.foreach(_.markForUpdate())
    dependentVars.foreach(_.onMarkingFinished())
  }
}

final class DepVar[A, I] private[util] (genBase: => I, genVal: I => Option[A], genDeps: I => Seq[Var[_]]) extends Var[A] {
  private[this] var lastBase = genBase
  private[this] var lastValue = genVal(lastBase)
  private[this] var lastDeps = genDeps(lastBase)
  private[this] var needsUpdate = false

  lastDeps.map(_.dependentVars += this)

  def get = {
    if (needsUpdate) {
      lastValue = genVal(lastBase)
      lastValue.foreach { value => dependentActions.foreach(_(value)) }
      needsUpdate = false
    }
    lastValue
  }

  private[util] def markForUpdate(): Unit = {
    if (!needsUpdate) {
      needsUpdate = true
      lastBase = genBase
      dependentVars.foreach(_.markForUpdate())
    }
  }

  private[util] def onMarkingFinished(): Unit = {
    if (needsUpdate) {
      lastDeps.map(_.dependentVars -= this)
      lastDeps = genDeps(lastBase)
      lastDeps.map(_.dependentVars += this)

      if (dependentActions.nonEmpty) get
    }
    dependentVars.foreach(_.onMarkingFinished())
  }
}
