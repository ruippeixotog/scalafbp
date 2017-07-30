package net.ruippeixotog.scalafbp.component.core

import akka.actor.Props

import net.ruippeixotog.scalafbp.component._

object ShowIcon extends Component {
  val name = "core/ShowIcon"
  val description = "Sets its own node icon according to received packets"
  val icon = Some("forward")

  val inPorts = List(InPort[String]("icon", "Font Awesome icon to show"))
  val outPorts = Nil

  val instanceProps = Props(new ComponentActor(this) {
    inPorts.head.stream.foreach { icon => broker ! ComponentActor.ChangeIcon(icon) }
  })
}
