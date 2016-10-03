package net.ruippeixotog.scalafbp.runtime

import monocle.function.At.at
import monocle.function.all._
import monocle.macros.GenLens
import monocle.std.map._
import monocle.{ Iso, Lens, Optional, Traversal }

class GraphStore extends Store[GraphStore.StoreType](Map.empty)

object GraphStore {
  type StoreType = Map[String, Graph]

  private def getOrElseIso[T](default: => T) = Iso[Option[T], T](_.getOrElse(default))(Some.apply)

  private def graphLens(graphId: String): Lens[StoreType, Option[Graph]] = at(graphId)

  private def graphLensOpt(graphId: String): Optional[StoreType, Graph] = index(graphId)

  private def nodeLens(graphId: String, nodeId: String): Optional[StoreType, Option[Node]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|-> at(nodeId)

  private def nodeLensOpt(graphId: String, nodeId: String): Optional[StoreType, Node] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|-? index(nodeId)

  private def edgeLens(graphId: String, src: PortRef, tgt: PortRef): Optional[StoreType, Option[Edge]] =
    nodeLensOpt(graphId, src.node) ^|-> GenLens[Node](_.edges) ^|-> at(src.port) ^<-> getOrElseIso(Map()) ^|-> at(tgt)

  private def initialLens(graphId: String, tgt: PortRef): Optional[StoreType, Option[Initial]] =
    nodeLensOpt(graphId, tgt.node) ^|-> GenLens[Node](_.initials) ^|-> at(tgt.port)

  private def publicInPortLens(graphId: String, publicId: String): Optional[StoreType, Option[PublicPort]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.publicIn) ^|-> at(publicId)

  private def publicOutPortLens(graphId: String, publicId: String): Optional[StoreType, Option[PublicPort]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.publicOut) ^|-> at(publicId)

  private def revEdgesLens(graphId: String): Traversal[StoreType, Map[PortRef, Edge]] =
    graphLensOpt(graphId) ^|-> GenLens[Graph](_.nodes) ^|->> each ^|-> GenLens[Node](_.edges) ^|->> each

  trait Key[A] extends Store.Key[StoreType, A] {
    def graphId: String
    def domain = graphId
  }

  trait RenamableKey[A] extends Key[A] with Store.RenamableKey[StoreType, A]

  case class GraphKey(graphId: String) extends Key[Graph] {
    val lens = graphLens(graphId).asOptional
  }

  case class NodeKey(graphId: String, nodeId: String) extends RenamableKey[Node] {
    val lens = nodeLens(graphId, nodeId)

    override def rename(to: Store.Key[StoreType, Node], curr: Node) =
      super.rename(to, curr).andThen(revEdgesLens(graphId).modify(_.map {
        case (tgt, e) => (if (tgt.node == nodeId) tgt.copy(node = to.asInstanceOf[NodeKey].nodeId) else tgt, e)
      }))
  }

  case class EdgeKey(graphId: String, src: PortRef, tgt: PortRef) extends Key[Edge] {
    val lens = edgeLens(graphId, src, tgt)
  }

  case class InitialKey(graphId: String, tgt: PortRef) extends Key[Initial] {
    val lens = initialLens(graphId, tgt)
  }

  case class PublicInPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = publicInPortLens(graphId, publicId)
  }

  case class PublicOutPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = publicOutPortLens(graphId, publicId)
  }

  type Request[A] = Store.Request[StoreType, A]
  type Response[A] = Store.Response[StoreType, A]
  type Event[A] = Store.Event[StoreType, A]
}
