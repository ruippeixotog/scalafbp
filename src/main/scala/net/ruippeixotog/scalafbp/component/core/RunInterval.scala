package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor._
import rx.lang.scala.Observable

import net.ruippeixotog.scalafbp.component._

case object RunInterval extends Component {
  val name = "core/RunInterval"
  val description = "Sends a signal periodically"
  val icon = Some("clock-o")
  val isSubgraph = true

  val intervalPort = InPort[Long]("interval", "Interval at which signals are emitted (ms)")
  val stopPort = InPort[Unit]("stop", "Stop the emission")
  val inPorts = List(intervalPort, stopPort)

  val outPort = OutPort[Unit]("out", "A signal sent at the given interval")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    override val terminationPolicy = Nil

    intervalPort.stream
      .switchMap { int => Observable.interval(int.millis).map(_ => ()) }
      .doOnCompleted(context.stop(self))
      .pipeTo(outPort)

    stopPort.stream.foreach(_ => context.stop(self))
  })
}
