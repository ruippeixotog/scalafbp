package net.ruippeixotog.scalafbp.runtime

import monocle.function.At.at
import monocle.function.all._
import monocle.macros.GenLens
import monocle.std.map._
import monocle.{ Iso, Lens, Optional, Traversal }

object GraphLenses {

  private[this] def getOrElseIso[T](default: => T) = Iso[Option[T], T](_.getOrElse(default))(Some.apply)

  def nodeLens(nodeId: String): Lens[Graph, Option[Node]] =
    GenLens[Graph](_.nodes) ^|-> at(nodeId)

  def nodeLensOpt(nodeId: String): Optional[Graph, Node] =
    GenLens[Graph](_.nodes) ^|-? index(nodeId)

  def edgeLens(src: PortRef, tgt: PortRef): Optional[Graph, Option[Edge]] =
    nodeLensOpt(src.node) ^|-> GenLens[Node](_.edges) ^|-> at(src.port) ^<-> getOrElseIso(Map()) ^|-> at(tgt)

  def initialLens(tgt: PortRef): Optional[Graph, Option[Initial]] =
    nodeLensOpt(tgt.node) ^|-> GenLens[Node](_.initials) ^|-> at(tgt.port)

  def publicInPortLens(publicId: String): Lens[Graph, Option[PublicPort]] =
    GenLens[Graph](_.publicIn) ^|-> at(publicId)

  def publicOutPortLens(publicId: String): Lens[Graph, Option[PublicPort]] =
    GenLens[Graph](_.publicOut) ^|-> at(publicId)

  val revEdgesLens: Traversal[Graph, Map[PortRef, Edge]] =
    GenLens[Graph](_.nodes) ^|->> each ^|-> GenLens[Node](_.edges) ^|->> each
}
