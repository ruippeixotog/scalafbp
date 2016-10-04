package net.ruippeixotog.scalafbp.runtime

import akka.actor.Props
import monocle._
import monocle.function.At.at
import monocle.function.all._
import monocle.std.map._

import net.ruippeixotog.scalafbp.component.Component

class ComponentRegistry extends Store[ComponentRegistry.StoreType](Map.empty) {
  def domain = { case _ => "" }
}

object ComponentRegistry {
  type StoreType = Map[String, Component]

  private def compLens(id: String): Lens[StoreType, Option[Component]] = at(id)

  case class ComponentKey(id: String) extends Store.Key[StoreType, Component] {
    val lens = compLens(id).asOptional
  }

  case object ComponentsKey extends Store.ListKey[StoreType, Component] {
    val lens = each[StoreType, Component]
  }

  val props = Props(new ComponentRegistry)

  def props(components: Iterable[Component]) = Props(new ComponentRegistry {
    components.foreach { comp => self ! Store.Create(ComponentKey(comp.name), comp) }
  })
}
