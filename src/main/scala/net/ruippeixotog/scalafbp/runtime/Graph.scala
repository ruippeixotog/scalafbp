package net.ruippeixotog.scalafbp.runtime

import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.Component

case class Node(component: Component, metadata: Map[String, JsValue])

case class PortRef(node: String, port: String) {
  override def toString = s"$node[$port]"
}

case class Edge(metadata: Map[String, JsValue])
case class Initial(value: JsValue, metadata: Map[String, JsValue])

case class Graph(
  id: String,
  nodes: Map[String, Node] = Map.empty,
  edges: Map[PortRef, Map[PortRef, Edge]] = Map.empty,
  initials: Map[PortRef, Initial] = Map.empty)
