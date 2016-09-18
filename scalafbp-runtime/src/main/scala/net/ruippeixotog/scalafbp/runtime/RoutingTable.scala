package net.ruippeixotog.scalafbp.runtime

/**
  * A data structure holding the current data routes for a running network.
  *
  * This class provides efficient bidirectional querying: both the routes from a source (out) port and the routes to a
  * target (in) port can be obtained in constant time. It also provides methods for deleting (closing) specific routes,
  * all routes from a source, all routes to a target or all routes to/from a node.
  *
  * This class is immutable - closing operations always return a new `RoutingTable`.
  */
class RoutingTable private (
    routeMap: Map[PortRef, Set[PortRef]],
    revRouteMap: Map[PortRef, Set[PortRef]],
    closedRouteHook: Option[(PortRef, PortRef) => Unit],
    closedSourceHook: Option[PortRef => Unit],
    closedTargetHook: Option[PortRef => Unit]) {

  /**
    * Returns all the routes in this table.
    *
    * @return an iterable of all the routes in this table as (source, target) pairs.
    */
  def routes: Iterable[(PortRef, PortRef)] = routeMap.toSeq.flatMap { case (src, tgts) => tgts.map(src -> _) }

  /**
    * Returns the ports to which a source port is connected.
    *
    * @param src the source port
    * @return an iterable of all the target ports `src` is connected to.
    */
  def routes(src: PortRef): Iterable[PortRef] = routeMap.getOrElse(src, Set.empty)

  /**
    * Returns the source ports connected to a target port.
    *
    * @param tgt the target port
    * @return an iterable of all the source ports connected to `tgt`.
    */
  def reverseRoutes(tgt: PortRef): Iterable[PortRef] = revRouteMap.getOrElse(tgt, Set.empty)

  /**
    * Opens a new route.
    *
    * @param src the source port
    * @param tgt the target port
    * @return a new `RoutingTable` with the given route opened.
    */
  def openRoute(src: PortRef, tgt: PortRef): RoutingTable = {
    val newRoutes = routeMap + (src -> (routeMap.getOrElse(src, Set.empty) + tgt))
    val newRevRoutes = revRouteMap + (tgt -> (routeMap.getOrElse(tgt, Set.empty) + src))
    copy(routes = newRoutes, revRoutes = newRevRoutes)
  }

  /**
    * Closes a single route.
    *
    * @param src the source port
    * @param tgt the target port
    * @return a new `RoutingTable` with the given route closed.
    */
  def closeRoute(src: PortRef, tgt: PortRef): RoutingTable =
    if (!routeMap.get(src).exists(_.contains(tgt))) this
    else closeRouteUnchecked(src, tgt)

  /**
    * Closes all the routes from a source port.
    *
    * @param src the source port
    * @return a new `RoutingTable` with the given source closed.
    */
  def closeSource(src: PortRef): RoutingTable =
    routeMap.getOrElse(src, Set.empty).foldLeft(this)(_.closeRouteUnchecked(src, _))

  /**
    * Closes all the routes to a target port.
    *
    * @param tgt the target port
    * @return a new `RoutingTable` with the given target closed.
    */
  def closeTarget(tgt: PortRef): RoutingTable =
    revRouteMap.getOrElse(tgt, Set.empty).foldLeft(this)(_.closeRouteUnchecked(_, tgt))

  /**
    * Closes all the routes to and from a node.
    *
    * @param node the node identifier
    * @return a new `RoutingTable` with the given node closed.
    */
  def closeNode(node: String): RoutingTable = {
    val disconnectedRoutes = routeMap.filter(_._1.node == node)
    val disconnectedRevRoutes = revRouteMap.filter(_._1.node == node)

    val updTable = disconnectedRoutes.foldLeft(this) {
      case (acc, (src, tgts)) =>
        tgts.foldLeft(acc)(_.closeRouteUnchecked(src, _))
    }

    disconnectedRevRoutes.foldLeft(updTable) {
      case (acc, (tgt, srcs)) =>
        srcs.foldLeft(acc)(_.closeRouteUnchecked(_, tgt))
    }
  }

  /**
    * Registers an action to perform when a route is closed.
    *
    * @param hook the action to perform when a route is closed
    * @return a new `RoutingTable` with the given action registered.
    */
  def onRouteClosed(hook: (PortRef, PortRef) => Unit): RoutingTable = copy(closedRouteHook = Some(hook))

  /**
    * Registers an action to perform when a source is closed.
    *
    * @param hook the action to perform when a source is closed
    * @return a new `RoutingTable` with the given action registered.
    */
  def onSourceClosed(hook: PortRef => Unit): RoutingTable = copy(closedSourceHook = Some(hook))

  /**
    * Registers an action to perform when a target is closed.
    *
    * @param hook the action to perform when a target is closed
    * @return a new `RoutingTable` with the given action registered.
    */
  def onTargetClosed(hook: PortRef => Unit): RoutingTable = copy(closedTargetHook = Some(hook))

  private def closeRouteUnchecked(src: PortRef, tgt: PortRef): RoutingTable = {
    closedRouteHook.foreach(_(src, tgt))

    val newSrcRoutes = routeMap.getOrElse(src, Set.empty) - tgt
    val newRoutes =
      if (newSrcRoutes.nonEmpty) routeMap + (src -> newSrcRoutes)
      else {
        closedSourceHook.foreach(_(src))
        routeMap - src
      }

    val newTgtRevRoutes = revRouteMap.getOrElse(tgt, Set.empty) - src
    val newRevRoutes =
      if (newTgtRevRoutes.nonEmpty) revRouteMap + (tgt -> newTgtRevRoutes)
      else {
        closedTargetHook.foreach(_(tgt))
        revRouteMap - tgt
      }

    copy(routes = newRoutes, revRoutes = newRevRoutes)
  }

  private[this] def copy(
    routes: Map[PortRef, Set[PortRef]] = routeMap,
    revRoutes: Map[PortRef, Set[PortRef]] = revRouteMap,
    closedRouteHook: Option[(PortRef, PortRef) => Unit] = closedRouteHook,
    closedSourceHook: Option[PortRef => Unit] = closedSourceHook,
    closedTargetHook: Option[PortRef => Unit] = closedTargetHook) = {

    new RoutingTable(routes, revRoutes, closedRouteHook, closedSourceHook, closedTargetHook)
  }
}

object RoutingTable {

  /**
    * Builds a `RoutingTable` from a graph.
    *
    * @param graph the graph to be used
    * @return the `RoutingTable` built from the given graph.
    */
  def apply(graph: Graph) = {

    // the initial routing table mapping outgoing ports to a list of destination ports to which packets should be sent
    val routes = graph.edges.mapValues(_.keySet)

    // the reversed index of initialRoutes, for checking which ports send data to each target port
    val revRoutes = routes.foldLeft(Map[PortRef, Set[PortRef]]()) {
      case (acc, (src, tgts)) =>
        tgts.foldLeft(acc) { (acc2, tgt) =>
          acc2 + (tgt -> (acc2.getOrElse(tgt, Set.empty) + src))
        }
    }

    new RoutingTable(routes, revRoutes, None, None, None)
  }
}
