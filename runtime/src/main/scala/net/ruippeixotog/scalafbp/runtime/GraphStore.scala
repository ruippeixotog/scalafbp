package net.ruippeixotog.scalafbp.runtime

import monocle.function.At.at
import monocle.function.all._
import monocle.std.map._
import monocle.{ Lens, Optional }

import net.ruippeixotog.scalafbp.runtime.GraphLenses._
import net.ruippeixotog.scalafbp.runtime.GraphStore.Domain

class GraphStore extends Store[GraphStore.StoreType](Map.empty) {
  def domains = {
    case key: GraphStore.Key[_] => List(Domain.all, Domain.graph(key.graphId))
    case GraphStore.GraphsKey => List(Domain.all)
    case key => throw new IllegalArgumentException(s"Wrong type of key provided: ${key.getClass.getName}")
  }
}

object GraphStore {
  type StoreType = Map[String, Graph]

  private def graphLens(graphId: String): Lens[StoreType, Option[Graph]] = at(graphId)
  private def graphLensOpt(graphId: String): Optional[StoreType, Graph] = index(graphId)

  trait Key[A] extends Store.Key[StoreType, A] {
    def graphId: String
  }

  trait RenamableKey[A] extends Key[A] with Store.RenamableKey[StoreType, A]

  case class GraphKey(graphId: String) extends Key[Graph] {
    val lens = graphLens(graphId).asOptional
  }

  case object GraphsKey extends Store.ListKey[StoreType, Graph] {
    val lens = each[StoreType, Graph]
  }

  case class NodeKey(graphId: String, nodeId: String) extends RenamableKey[Node] {
    val lens = graphLensOpt(graphId) ^|-> nodeLens(nodeId)

    private[this] val revLens = graphLensOpt(graphId) ^|->> revEdgesLens

    override def rename(to: Store.Key[StoreType, Node], curr: Node) =
      super.rename(to, curr).andThen(revLens.modify(_.map {
        case (tgt, e) => (if (tgt.node == nodeId) tgt.copy(node = to.asInstanceOf[NodeKey].nodeId) else tgt, e)
      }))
  }

  case class EdgeKey(graphId: String, src: PortRef, tgt: PortRef) extends Key[Edge] {
    val lens = graphLensOpt(graphId) ^|-? edgeLens(src, tgt)
  }

  case class InitialKey(graphId: String, tgt: PortRef) extends Key[Initial] {
    val lens = graphLensOpt(graphId) ^|-? initialLens(tgt)
  }

  case class PublicInPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = graphLensOpt(graphId) ^|-> publicInPortLens(publicId)
  }

  case class PublicOutPortKey(graphId: String, publicId: String) extends RenamableKey[PublicPort] {
    val lens = graphLensOpt(graphId) ^|-> publicOutPortLens(publicId)
  }

  type Request[A] = Store.Request[StoreType, A]
  type Response[A] = Store.Response[StoreType, A]
  type Event[A] = Store.Event[StoreType, A]

  object Domain {
    val all = ""
    def graph(graphId: String) = s"g-$graphId"
  }
}
