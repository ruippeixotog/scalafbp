package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor._
import rx.lang.scala.Observable

import net.ruippeixotog.scalafbp.component.SimpleComponentActor.RxDefinition
import net.ruippeixotog.scalafbp.component.{ Component, InPort, OutPort }

case object RunTimeout extends Component {
  val name = "core/RunTimeout"
  val description = "Sends a signal after the given time"
  val icon = Some("clock-o")
  val isSubgraph = true

  val timePort = InPort[Long]("time", "Time after which a signal will be sent (ms)")
  val inPorts = List(timePort)

  val outPort = OutPort[Unit]("out", "A signal sent after the given time")
  val outPorts = List(outPort)

  val instanceProps = Props(new Actor with RxDefinition {
    def component = RunTimeout

    timePort.stream.take(1)
      .flatMap { t => Observable.timer(t.millis).map(_ => ()) }
      .doOnCompleted(context.stop(self))
      .pipeTo(outPort)
  })
}
