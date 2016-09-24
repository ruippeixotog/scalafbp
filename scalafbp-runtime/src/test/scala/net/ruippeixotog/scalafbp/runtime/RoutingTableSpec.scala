package net.ruippeixotog.scalafbp.runtime

import org.specs2.mutable.Specification

import net.ruippeixotog.scalafbp.component.DummyComponent

class RoutingTableSpec extends Specification {

  val edgeData = Edge()
  def ref(node: String, port: String) = PortRef(node, port)

  /*
              /¯¯
       ____ n2 ____
      /            \
  n1 -              - n4
      \____ n3 ____/
         __/
  */
  val graph = Graph("test1", Map(
    "n1" -> Node(DummyComponent(0, 1), edges = Map(
      "out1" -> Map(ref("n2", "in1") -> edgeData, ref("n3", "in1") -> edgeData))),
    "n2" -> Node(DummyComponent(1, 2), edges = Map(
      "out1" -> Map(ref("n4", "in1") -> edgeData))),
    "n3" -> Node(DummyComponent(2, 1), edges = Map(
      "out1" -> Map(ref("n4", "in1") -> edgeData))),
    "n4" -> Node(DummyComponent(1, 0))))

  val table = RoutingTable(graph)

  "A RoutingTable" should {

    "be correctly built off a graph" in {
      table.routes(ref("n1", "out1")).toSet mustEqual Set(ref("n2", "in1"), ref("n3", "in1"))
      table.routes(ref("n2", "out1")).toSet mustEqual Set(ref("n4", "in1"))
      table.routes(ref("n2", "out2")).toSet mustEqual Set.empty

      table.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set(ref("n1", "out1"))
      table.reverseRoutes(ref("n4", "in1")).toSet mustEqual Set(ref("n2", "out1"), ref("n3", "out1"))
      table.reverseRoutes(ref("n3", "in2")).toSet mustEqual Set.empty

      table.routes.toSet mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n2", "out1") -> ref("n4", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))
    }

    "be updated correctly when a route is closed" in {
      val newTable = table.closeRoute(ref("n1", "out1"), ref("n2", "in1"))
      newTable.routes(ref("n1", "out1")).toSet mustEqual Set(ref("n3", "in1"))
      newTable.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set.empty
      newTable.routes must not(contain(ref("n1", "out1") -> ref("n2", "in1")))
    }

    "be updated correctly when a new route is opened" in {
      val newTable = table.openRoute(ref("n2", "out2"), ref("n3", "in2"))
      newTable.routes(ref("n2", "out2")).toSet mustEqual Set(ref("n3", "in2"))
      newTable.reverseRoutes(ref("n3", "in2")).toSet mustEqual Set(ref("n2", "out2"))
      newTable.routes must contain(ref("n2", "out2") -> ref("n3", "in2"))

      val newTable2 = table.openRoute(ref("n2", "out1"), ref("n3", "in1"))
      newTable2.routes(ref("n2", "out1")).toSet mustEqual Set(ref("n3", "in1"), ref("n4", "in1"))
      newTable2.reverseRoutes(ref("n3", "in1")).toSet mustEqual Set(ref("n1", "out1"), ref("n2", "out1"))
      newTable2.routes must contain(ref("n2", "out1") -> ref("n3", "in1"))
    }

    "be updated correctly when an out port is closed" in {
      val newTable = table.closeSource(ref("n1", "out1"))
      newTable.routes(ref("n1", "out1")).toSet mustEqual Set.empty
      newTable.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set.empty
      newTable.reverseRoutes(ref("n3", "in1")).toSet mustEqual Set.empty
      newTable.routes.toSet mustEqual Set(
        ref("n2", "out1") -> ref("n4", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))
    }

    "be updated correctly when an in port is closed" in {
      val newTable = table.closeTarget(ref("n4", "in1"))
      newTable.routes(ref("n2", "out1")).toSet mustEqual Set.empty
      newTable.routes(ref("n3", "out1")).toSet mustEqual Set.empty
      newTable.reverseRoutes(ref("n4", "in1")).toSet mustEqual Set.empty
      newTable.routes.toSet mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"))
    }

    "be updated correctly when a node is closed" in {
      val newTable = table.closeNode("n2")
      newTable.routes(ref("n2", "out1")).toSet mustEqual Set.empty
      newTable.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set.empty
      newTable.routes.toSet mustEqual Set(
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))
    }

    "call hooks when routes, sources or targets are closed" in {
      var closedRoutes = Set.empty[(PortRef, PortRef)]
      var closedSources = Set.empty[PortRef]
      var closedTargets = Set.empty[PortRef]

      def clearHooks() = {
        closedSources = Set.empty
        closedTargets = Set.empty
        closedRoutes = Set.empty
      }

      val newTable = table
        .onRouteClosed { (src, tgt) => closedRoutes += ((src, tgt)) }
        .onSourceClosed(closedSources += _)
        .onTargetClosed(closedTargets += _)

      newTable.closeRoute(ref("n2", "out1"), ref("n4", "in1"))
      closedSources mustEqual Set(ref("n2", "out1"))
      closedTargets mustEqual Nil
      closedRoutes mustEqual Set(ref("n2", "out1") -> ref("n4", "in1"))

      clearHooks()
      newTable.closeSource(ref("n1", "out1"))
      closedSources mustEqual Set(ref("n1", "out1"))
      closedTargets mustEqual Set(ref("n2", "in1"), ref("n3", "in1"))
      closedRoutes mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"))

      clearHooks()
      newTable.closeTarget(ref("n2", "in1"))
      closedSources mustEqual Nil
      closedTargets mustEqual Set(ref("n2", "in1"))
      closedRoutes mustEqual Set(ref("n1", "out1") -> ref("n2", "in1"))

      clearHooks()
      newTable.closeNode("n3")
      closedSources mustEqual Set(ref("n3", "out1"))
      closedTargets mustEqual Set(ref("n3", "in1"))
      closedRoutes mustEqual Set(
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))
    }
  }
}
