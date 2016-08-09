package net.ruippeixotog.scalafbp.util

import org.specs2.mutable.Specification

class VarSpec extends Specification {

  "A Var" should {

    "have proper constructors" in {
      Var.undefined[Int]().get must beNone
      Var(2).get must beSome(2)
      Var.constant(2).get must beSome(2)

      val x = Var.undefined[Int]()
      x.get must beNone
      x.set(1)
      x.get must beSome(1)
      x.set(2)
      x.get must beSome(2)
    }

    "have a correct map method" in {
      val x = Var.undefined[String]()
      val y = x.map(_.length)
      y.get must beNone

      x.set("abcde")
      y.get must beSome(5)
    }

    "have a correct flatMap method" in {
      val x = Var.undefined[Int]()
      val y = Var.undefined[Int]()

      val sum = x.flatMap { xv => y.map(_ + xv) }
      sum.get must beNone

      y.set(2)
      x.get must beNone
      sum.get must beNone

      x.set(10)
      y.get must beSome(2)
      sum.get must beSome(12)

      y.set(100)
      x.get must beSome(10)
      sum.get must beSome(110)
    }

    "have a correct flatten method" in {
      val x = Var.undefined[Int]()
      val y = Var.undefined[Int]()

      val xy = x.map { xVal => y.map(_ + xVal) }
      val sum = xy.flatten
      sum.get must beNone

      y.set(2)
      x.get must beNone
      sum.get must beNone

      x.set(10)
      y.get must beSome(2)
      sum.get must beSome(12)

      y.set(100)
      x.get must beSome(10)
      sum.get must beSome(110)
    }

    "have a correct foreach method" in {
      val x = Var("aaa")
      val y = x.map(_.length)
      val z = Var.undefined[String]()

      var lastValueX = ""
      var lastValueY = 0
      var lastValueZ = ""
      x.foreach { xVal => lastValueX = xVal }
      y.foreach { yVal => lastValueY = yVal }
      z.foreach { zVal => lastValueZ = zVal }

      lastValueX mustEqual "aaa"
      lastValueY mustEqual 3
      lastValueZ mustEqual ""

      x.set("abcde")
      lastValueX mustEqual "abcde"
      lastValueY mustEqual 5

      z.set("qwerty")
      lastValueZ mustEqual "qwerty"
    }

    "have a correct zip method" in {
      val x = Var.undefined[Int]()
      val y = Var.undefined[Int]()
      val xy = x.zip(y)

      xy.get must beNone

      x.set(1)
      xy.get must beNone

      y.set(10)
      xy.get must beSome((1, 10))

      x.set(2)
      xy.get must beSome((2, 10))

      y.set(20)
      xy.get must beSome((2, 20))
    }

    "have a correct unzip method" in {
      val xy = Var.undefined[(Int, Int)]()
      val (x, y) = xy.unzip

      x.get must beNone
      y.get must beNone

      xy.set((1, 10))
      x.get must beSome(1)
      y.get must beSome(10)

      xy.set((2, 20))
      x.get must beSome(2)
      y.get must beSome(20)
    }

    "update correctly the dynamic dependencies" in {
      val a = Var.undefined[Int]()
      val b = Var.undefined[Int]()
      val switch = Var(false)
      val res = switch.flatMap { sw => if (sw) a else b }

      res.get must beNone

      var called = false
      res.foreach { _ => called = true }
      called must beFalse

      a.set(3)
      called must beFalse
      res.get must beNone

      b.set(5)
      called must beTrue
      res.get must beSome(5)
      called = false

      a.set(2)
      called must beFalse
      res.get must beSome(5)

      b.set(7)
      called must beTrue
      res.get must beSome(7)
      called = false

      switch.set(true)
      called must beTrue
      res.get must beSome(2)
      called = false

      a.set(1)
      called must beTrue
      res.get must beSome(1)
      called = false

      b.set(10)
      called must beFalse
      res.get must beSome(1)
    }

    "handle correctly doubly dependent variables" in {
      val a = Var(3)
      val b = Var(10)
      val c = a.flatMap { aVal => if (aVal < 5) a else b }

      c.get must beSome(3)
      a.set(4)
      c.get must beSome(4)

      a.set(8)
      c.get must beSome(10)

      a.set(1)
      c.get must beSome(1)
    }

    "handle correctly a complex dependency graph" in {
      val a = Var("abcba")
      val b = a.map(_.length)
      val c = a.map(_.distinct.length)
      val d = Var.undefined[Int]()
      val e = for { bVal <- b; cVal <- c; dVal <- d } yield bVal + cVal + dVal

      var called = List.empty[(Char, Any)]
      a.foreach { aVal => called +:= ('a' -> aVal) }
      b.foreach { bVal => called +:= ('b' -> bVal) }
      c.foreach { cVal => called +:= ('c' -> cVal) }
      d.foreach { dVal => called +:= ('d' -> dVal) }
      e.foreach { eVal => called +:= ('e' -> eVal) }

      called must containTheSameElementsAs(Seq('a' -> "abcba", 'b' -> 5, 'c' -> 3))
      called = Nil

      d.set(1)
      called must containTheSameElementsAs(Seq('d' -> 1, 'e' -> 9))
      called = Nil

      a.set("ab")
      called must containTheSameElementsAs(Seq('a' -> "ab", 'b' -> 2, 'c' -> 2, 'e' -> 5))
      called = Nil

      d.set(2)
      called must containTheSameElementsAs(Seq('d' -> 2, 'e' -> 6))
    }
  }
}
