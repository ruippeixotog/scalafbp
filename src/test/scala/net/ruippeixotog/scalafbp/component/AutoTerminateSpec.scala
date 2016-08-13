package net.ruippeixotog.scalafbp.component

trait AutoTerminateSpec { this: ComponentSpec =>

  def terminateItselfWhenAllInPortsAreClosed =
    "terminate itself when all in ports are closed" in new ComponentInstance {
      component.inPorts.foreach(_.close())
      this must terminate()
    }
}
