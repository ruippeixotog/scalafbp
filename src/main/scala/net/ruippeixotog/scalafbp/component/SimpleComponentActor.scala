package net.ruippeixotog.scalafbp.component

import akka.actor.Actor
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

import net.ruippeixotog.scalafbp.component.ComponentActor._
import net.ruippeixotog.scalafbp.component.SimpleComponentActor._

abstract class SimpleComponentActor(val component: Component) extends Actor with AutoTerminate

object SimpleComponentActor {

  trait AutoTerminate extends ReceivePipeline { this: Actor =>
    def component: Component

    private[this] var openInPorts = component.inPorts.length

    pipelineInner {
      case msg @ InPortDisconnected(port) =>
        openInPorts -= 1
        if (openInPorts == 0) context.stop(self)
        Inner(msg)
    }
  }
}
