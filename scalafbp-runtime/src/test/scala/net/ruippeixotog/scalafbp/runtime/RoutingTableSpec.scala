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
    "n1" -> Node(DummyComponent[String](0, 1), edges = Map(
      "out1" -> Map(ref("n2", "in1") -> edgeData, ref("n3", "in1") -> edgeData))),
    "n2" -> Node(DummyComponent[String](1, 2), edges = Map(
      "out1" -> Map(ref("n4", "in1") -> edgeData))),
    "n3" -> Node(DummyComponent[String](2, 1), edges = Map(
      "out1" -> Map(ref("n4", "in1") -> edgeData))),
    "n4" -> Node(DummyComponent[String](1, 0))))

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

    "be correctly built off a graph with external ports" in {
      val graph2 = graph.copy(
        publicIn = Map("pubIn" -> PublicPort(ref("n2", "in1"))),
        publicOut = Map("pubOut" -> PublicPort(ref("n2", "out2"))))

      val table2 = RoutingTable().loadGraph(graph2, Some("ext"))

      table2.routes(ref("n1", "out1")).toSet mustEqual Set(ref("n2", "in1"), ref("n3", "in1"))
      table2.routes(ref("n2", "out1")).toSet mustEqual Set(ref("n4", "in1"))
      table2.routes(ref("n2", "out2")).toSet mustEqual Set(ref("ext", "pubOut"))

      table2.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set(ref("n1", "out1"), ref("ext", "pubIn"))
      table2.reverseRoutes(ref("n4", "in1")).toSet mustEqual Set(ref("n2", "out1"), ref("n3", "out1"))
      table2.reverseRoutes(ref("n3", "in2")).toSet mustEqual Set.empty

      table2.routes.toSet mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n2", "out1") -> ref("n4", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"),
        ref("ext", "pubIn") -> ref("n2", "in1"),
        ref("n2", "out2") -> ref("ext", "pubOut"))
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

    "be updated correctly when all routes are closed" in {
      val newTable = table.closeAll
      newTable.routes(ref("n2", "out1")).toSet mustEqual Set.empty
      newTable.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set.empty
      newTable.routes.toSet mustEqual Set.empty
    }

    "be updated correctly when a new graph is loaded" in {
      val newGraph = Graph("test1", Map(
        "a" -> Node(DummyComponent[String](0, 1), edges = Map("out1" -> Map(ref("b", "in1") -> edgeData))),
        "b" -> Node(DummyComponent[String](1, 0))))

      val newTable = table.loadGraph(newGraph)
      newTable.routes(ref("a", "out1")).toSet mustEqual Set(ref("b", "in1"))
      newTable.reverseRoutes(ref("n2", "in1")).toSet mustEqual Set.empty
      newTable.routes.toSet mustEqual Set(ref("a", "out1") -> ref("b", "in1"))
    }

    "call hooks when routes, sources or targets are opened or closed" in {
      var closedSources = Set.empty[PortRef]
      var closedTargets = Set.empty[PortRef]
      var openedRoutes = Set.empty[(PortRef, PortRef)]
      var closedRoutes = Set.empty[(PortRef, PortRef)]

      def clearHooks() = {
        closedTargets = Set.empty
        closedRoutes = Set.empty
        openedRoutes = Set.empty
        closedSources = Set.empty
      }

      val newTable = table
        .onSourceClosed(closedSources += _)
        .onTargetClosed(closedTargets += _)
        .onRouteOpened { (src, tgt) => openedRoutes += ((src, tgt)) }
        .onRouteClosed { (src, tgt) => closedRoutes += ((src, tgt)) }

      newTable.openRoute(ref("n2", "out2"), ref("n3", "in2"))
      closedRoutes mustEqual Nil
      closedTargets mustEqual Nil
      openedRoutes mustEqual Set(ref("n2", "out2") -> ref("n3", "in2"))
      closedRoutes mustEqual Nil

      clearHooks()
      newTable.closeRoute(ref("n2", "out1"), ref("n4", "in1"))
      closedSources mustEqual Set(ref("n2", "out1"))
      closedTargets mustEqual Nil
      openedRoutes mustEqual Nil
      closedRoutes mustEqual Set(ref("n2", "out1") -> ref("n4", "in1"))

      clearHooks()
      newTable.closeSource(ref("n1", "out1"))
      closedSources mustEqual Set(ref("n1", "out1"))
      closedTargets mustEqual Set(ref("n2", "in1"), ref("n3", "in1"))
      openedRoutes mustEqual Nil
      closedRoutes mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"))

      clearHooks()
      newTable.closeTarget(ref("n2", "in1"))
      closedSources mustEqual Nil
      closedTargets mustEqual Set(ref("n2", "in1"))
      openedRoutes mustEqual Nil
      closedRoutes mustEqual Set(ref("n1", "out1") -> ref("n2", "in1"))

      clearHooks()
      newTable.closeNode("n3")
      closedSources mustEqual Set(ref("n3", "out1"))
      closedTargets mustEqual Set(ref("n3", "in1"))
      openedRoutes mustEqual Nil
      closedRoutes mustEqual Set(
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))

      clearHooks()
      newTable.closeAll
      closedSources mustEqual Set(ref("n1", "out1"), ref("n2", "out1"), ref("n3", "out1"))
      closedTargets mustEqual Set(ref("n2", "in1"), ref("n3", "in1"), ref("n4", "in1"))
      openedRoutes mustEqual Nil
      closedRoutes mustEqual Set(
        ref("n1", "out1") -> ref("n2", "in1"),
        ref("n1", "out1") -> ref("n3", "in1"),
        ref("n2", "out1") -> ref("n4", "in1"),
        ref("n3", "out1") -> ref("n4", "in1"))
    }
  }
}
