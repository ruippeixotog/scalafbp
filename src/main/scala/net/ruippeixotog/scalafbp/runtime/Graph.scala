package net.ruippeixotog.scalafbp.runtime

import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.Component

case class Node(component: Component, metadata: Map[String, JsValue])

sealed trait InConnection {
  def metadata: Map[String, JsValue]
}

case class PortRef(node: String, port: String) {
  override def toString = s"$node[$port]"
}

case class Edge(src: PortRef, metadata: Map[String, JsValue]) extends InConnection
case class IIP(value: JsValue, metadata: Map[String, JsValue]) extends InConnection

case class Graph(
  id: String,
  nodes: Map[String, Node] = Map.empty,
  connections: Map[PortRef, InConnection] = Map.empty)
