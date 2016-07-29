package net.ruippeixotog.scalafbp.component

import spray.json.JsValue

case class Node(component: Component, metadata: Map[String, String])

sealed trait InConnection {
  def metadata: Map[String, String]
}

case class Edge(src: String, srcPort: String, metadata: Map[String, String]) extends InConnection
case class IIP(value: Any, metadata: Map[String, String]) extends InConnection

case class Graph(
  id: String,
  nodes: Map[String, Node] = Map.empty,
  connections: Map[(String, String), InConnection] = Map.empty)
