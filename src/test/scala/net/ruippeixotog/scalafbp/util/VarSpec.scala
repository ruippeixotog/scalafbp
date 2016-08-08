package net.ruippeixotog.scalafbp.util

import org.specs2.mutable.Specification

class VarSpec extends Specification {

  "A Var" should {

    "have proper constructors" in {
      Var(2).get mustEqual 2

      val x = Var.source(2)
      x.get mustEqual 2
      x.set(1)
      x.get mustEqual 1
    }

    "have a correct map method" in {
      val x = Var.source("aaa")
      val y = x.map(_.length)
      y.get mustEqual 3

      x.set("abcde")
      y.get mustEqual 5
    }

    "have a correct flatMap method" in {
      val x = Var.source(3)
      val y = Var.source(2)

      val sum = x.flatMap { xv => y.map(_ + xv) }
      sum.get mustEqual 5

      x.set(10)
      y.get mustEqual 2
      sum.get mustEqual 12

      y.set(100)
      x.get mustEqual 10
      sum.get mustEqual 110
    }

    "have a correct flatten method" in {
      val x = Var.source(3)
      val y = Var.source(2)

      val xy = x.map { xVal => y.map(_ + xVal) }
      val sum = xy.flatten
      sum.get mustEqual 5

      x.set(10)
      y.get mustEqual 2
      sum.get mustEqual 12

      y.set(100)
      x.get mustEqual 10
      sum.get mustEqual 110
    }

    "have a correct foreach method" in {
      val x = Var.source("aaa")
      val y = x.map(_.length)

      var lastValueX = ""
      var lastValueY = 0
      x.foreach { xVal => lastValueX = xVal }
      y.foreach { yVal => lastValueY = yVal }

      lastValueX mustEqual "aaa"
      lastValueY mustEqual 3

      x.set("abcde")
      lastValueX mustEqual "abcde"
      lastValueY mustEqual 5
    }

    "have a correct zip method" in {
      val x = Var.source(3)
      val y = Var.source(5)
      val xy = x.zip(y)

      xy.get mustEqual (3, 5)

      x.set(1)
      xy.get mustEqual (1, 5)

      y.set(10)
      xy.get mustEqual (1, 10)
    }

    "have a correct unzip method" in {
      val xy = Var.source((3, 5))
      val (x, y) = xy.unzip

      x.get mustEqual 3
      y.get mustEqual 5

      xy.set((1, 10))
      x.get mustEqual 1
      y.get mustEqual 10
    }

    "update correctly the dynamic dependencies" in {
      val a = Var.source(3)
      val b = Var.source(5)
      val switch = Var.source(false)
      val res = switch.flatMap { sw => if (sw) a else b }

      res.get mustEqual 5

      var called = false
      res.foreach { _ => called = true }
      called must beTrue

      called = false
      a.set(2)
      called must beFalse
      res.get mustEqual 5

      b.set(7)
      called must beTrue
      res.get mustEqual 7

      called = false
      switch.set(true)
      called must beTrue
      res.get mustEqual 2

      called = false
      a.set(1)
      called must beTrue
      res.get mustEqual 1

      called = false
      b.set(10)
      called must beFalse
      res.get mustEqual 1
    }

    "handle correctly doubly dependent variables" in {
      val a = Var.source(3)
      val b = Var.source(10)
      val c = a.flatMap { aVal => if (aVal < 5) a else b }

      c.get mustEqual 3
      a.set(4)
      c.get mustEqual 4

      a.set(8)
      c.get mustEqual 10

      a.set(1)
      c.get mustEqual 1
    }

    "handle correctly a complex dependency graph" in {
      val a = Var.source("abcba")
      val b = a.map(_.length)
      val c = a.map(_.distinct.length)
      val d = Var.source(1)
      val e = for { bVal <- b; cVal <- c; dVal <- d } yield bVal + cVal + dVal

      var called = List.empty[(Char, Any)]
      a.foreach { aVal => called +:= ('a' -> aVal) }
      b.foreach { bVal => called +:= ('b' -> bVal) }
      c.foreach { cVal => called +:= ('c' -> cVal) }
      d.foreach { dVal => called +:= ('d' -> dVal) }
      e.foreach { eVal => called +:= ('e' -> eVal) }

      called must containTheSameElementsAs(Seq('a' -> "abcba", 'b' -> 5, 'c' -> 3, 'd' -> 1, 'e' -> 9))

      called = Nil
      a.set("ab")
      c.get mustEqual 2
      called must containTheSameElementsAs(Seq('a' -> "ab", 'b' -> 2, 'c' -> 2, 'e' -> 5))

      called = Nil
      d.set(2)
      called must containTheSameElementsAs(Seq('d' -> 2, 'e' -> 6))
    }
  }
}
