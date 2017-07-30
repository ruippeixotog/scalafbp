package net.ruippeixotog.scalafbp.component.core

import net.ruippeixotog.scalafbp.component._

class ShowIconSpec extends ComponentSpec with AutoTerminateSpec {
  val component = ShowIcon

  "A ShowIcon component" should {

    "change its icon every time it receives a packet with the icon name" in new ComponentInstance {
      ShowIcon.inPorts("icon").send("arrow")
      this must sendChangeIcon("arrow")

      ShowIcon.inPorts("icon").send("cog")
      this must sendChangeIcon("cog")
    }

    terminateItselfWhenAllInPortsAreClosed
  }
}
