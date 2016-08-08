package net.ruippeixotog.scalafbp.util

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom

sealed abstract class Var[+A] {
  def get: A

  final def map[B](f: A => B): Var[B] = new DepVar[B, Unit]((), _ => f(get), _ => List(this))

  final def flatMap[B](f: A => Var[B]): Var[B] = new DepVar[B, Var[B]](f(get), _.get, List(this, _))

  final def flatten[B](implicit ev: A <:< Var[B]): Var[B] = new DepVar[B, Var[B]](get, _.get, List(this, _))

  final def foreach[U](f: A => U): Unit = {
    dependentActions :+= f
    f(get)
  }

  final def zip[B](v: Var[B]): Var[(A, B)] = new DepVar[(A, B), Unit]((), _ => (get, v.get), _ => List(this, v))
  final def unzip[A1, A2](implicit ev: A => (A1, A2)): (Var[A1], Var[A2]) = (map(_._1), map(_._2))

  protected[util] var dependentVars: Set[DepVar[_, _]] = Set.empty
  protected[this] var dependentActions: Seq[A => Any] = Vector.empty
}

object Var {
  def apply[A](a: A): Var[A] = new Source(a)
  def source[A](a: A): Source[A] = new Source(a)

  def sequence[A, M[X] <: SeqLike[X, M[X]]](in: M[Var[A]])(
    implicit
    cbf: CanBuildFrom[M[Var[A]], A, M[A]],
    cbf2: CanBuildFrom[M[A], A, M[A]]): Var[M[A]] = {

    in.foldRight(Var(cbf(in).result())) { (v, acc) =>
      for (builder <- acc; elem <- v) yield elem +: builder
    }
  }
}

final class Source[A] private[util] (private[this] var value: A) extends Var[A] {
  def get = value

  def set(a: A): Unit = {
    value = a
    dependentActions.foreach(_(value))
    dependentVars.foreach(_.markForUpdate())
    dependentVars.foreach(_.onMarkingFinished())
  }
}

final class DepVar[A, I] private[util] (genBase: => I, genVal: I => A, genDeps: I => Seq[Var[_]]) extends Var[A] {
  private[this] var lastBase = genBase
  private[this] var lastValue = genVal(lastBase)
  private[this] var lastDeps = genDeps(lastBase)
  private[this] var needsUpdate = false

  lastDeps.map(_.dependentVars += this)

  def get = {
    if (needsUpdate) {
      lastValue = genVal(lastBase)
      dependentActions.foreach(_(lastValue))
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
