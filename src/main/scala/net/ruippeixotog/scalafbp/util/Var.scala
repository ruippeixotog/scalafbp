package net.ruippeixotog.scalafbp.util

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom

sealed abstract class Var[+A] {
  def get: A

  final def map[B](f: A => B): Var[B] = newDepVar(f(get))
  final def flatMap[B](f: A => Var[B]): Var[B] = newDepVar(f(get).get)
  final def flatten[B](implicit ev: A <:< Var[B]): Var[B] = newDepVar(get.get)

  final def foreach[U](f: A => U): Unit = {
    dependentActions :+= f
    f(get)
  }

  final def zip[B](v: Var[B]): Var[(A, B)] = newDepVar((get, v.get))
  final def unzip[A1, A2](implicit ev: A => (A1, A2)): (Var[A1], Var[A2]) = (newDepVar(get._1), newDepVar(get._2))

  protected[this] var dependentVars: Seq[DepVar[_]] = Vector.empty
  protected[this] var dependentActions: Seq[A => Any] = Vector.empty

  private[this] def newDepVar[B](gen: => B): DepVar[B] = {
    val v = new DepVar(gen)
    dependentVars :+= v
    v
  }
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

final class Source[A](private[this] var value: A) extends Var[A] {
  def get = value

  def set(a: A): Unit = {
    value = a
    dependentActions.foreach(_(value))
    dependentVars.foreach(_.markForUpdate())
    dependentVars.foreach(_.onMarkingFinished())
  }
}

final class DepVar[A] private[util] (gen: => A) extends Var[A] {

  def get = {
    if (needsUpdate) {
      lastValue = gen
      needsUpdate = false
    }
    lastValue
  }

  private[this] var lastValue = gen
  private[this] var needsUpdate = false

  private[util] def markForUpdate(): Unit = {
    if (!needsUpdate) {
      needsUpdate = true
      dependentVars.foreach(_.markForUpdate())
    }
  }

  private[util] def onMarkingFinished(): Unit = {
    if (needsUpdate && dependentActions.nonEmpty) {
      val value = get
      dependentActions.foreach(_(value))
    }
    dependentVars.foreach(_.onMarkingFinished())
  }
}
