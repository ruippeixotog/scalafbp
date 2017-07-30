package net.ruippeixotog.scalafbp.runtime

import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case class Graph(
    id: String,
    nodes: Map[String, Node] = Map.empty,
    publicIn: Map[String, PublicPort] = Map.empty,
    publicOut: Map[String, PublicPort] = Map.empty) {

  def edges: Map[PortRef, Map[PortRef, Edge]] =
    for { (srcNode, node) <- nodes; (srcPort, tgts) <- node.edges }
      yield (PortRef(srcNode, srcPort), tgts)

  def initials: Map[PortRef, Initial] =
    for { (tgtNode, node) <- nodes; (tgtPort, initials) <- node.initials }
      yield (PortRef(tgtNode, tgtPort), initials)

  def inPortInfo(ref: PortRef): Option[InPort[_]] =
    nodes.get(ref.node).flatMap(_.component.inPorts.find(_.id == ref.port))

  def outPortInfo(ref: PortRef): Option[OutPort[_]] =
    nodes.get(ref.node).flatMap(_.component.outPorts.find(_.id == ref.port))
}

case class Node(
  component: Component,
  metadata: Map[String, JsValue] = Map.empty,
  edges: Map[String, Map[PortRef, Edge]] = Map.empty,
  initials: Map[String, Initial] = Map.empty)

case class PortRef(node: String, port: String) {
  override def toString = s"$node[$port]"
}

case class Edge(metadata: Map[String, JsValue] = Map.empty)
case class Initial(value: JsValue, metadata: Map[String, JsValue] = Map.empty)
case class PublicPort(internal: PortRef, metadata: Map[String, JsValue] = Map.empty)
