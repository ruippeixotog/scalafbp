package net.ruippeixotog.scalafbp.component

object ComponentRegistry {
  val registry = Map[String, Component](
    "core/Repeat" -> core.Repeat,
    "core/Output" -> core.Output)
}
