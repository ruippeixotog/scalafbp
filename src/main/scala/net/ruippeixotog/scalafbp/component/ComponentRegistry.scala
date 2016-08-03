package net.ruippeixotog.scalafbp.component

trait ComponentRegistry extends Iterable[Component] {
  def get(id: String): Option[Component]
}

class MapComponentRegistry(map: Map[String, Component]) extends ComponentRegistry {
  def get(id: String) = map.get(id)
  def iterator = map.valuesIterator
  override def size = map.size
}

object DefaultComponentRegistry extends MapComponentRegistry(Map(
  "core/Repeat" -> core.Repeat,
  "core/Output" -> core.Output))
