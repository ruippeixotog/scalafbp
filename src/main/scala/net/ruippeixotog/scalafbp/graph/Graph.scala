package net.ruippeixotog.scalafbp.graph

import spray.json.JsValue

import net.ruippeixotog.scalafbp.component.Component

case class Node(component: Component, metadata: Map[String, JsValue])

sealed trait InConnection {
  def metadata: Map[String, JsValue]
}

case class Edge(src: String, srcPort: String, metadata: Map[String, JsValue]) extends InConnection
case class IIP(value: JsValue, metadata: Map[String, JsValue]) extends InConnection

case class Graph(
  id: String,
  nodes: Map[String, Node] = Map.empty,
  connections: Map[(String, String), InConnection] = Map.empty)
