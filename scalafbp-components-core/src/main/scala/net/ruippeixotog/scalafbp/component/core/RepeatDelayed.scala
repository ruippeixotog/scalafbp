package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import akka.actor.Props
import rx.lang.scala.Observable
import spray.json.JsValue

import net.ruippeixotog.scalafbp.component._

case object RepeatDelayed extends Component {
  val name = "core/RepeatDelayed"
  val description = "Forwards packets after a set delay"
  val icon = Some("clock-o")
  val isSubgraph = true

  val inPort = InPort[JsValue]("in", "Packet to forward with a delay")
  val delayPort = InPort[Long]("delay", "Delay length (ms)")
  val inPorts = List(inPort, delayPort)

  val outPort = OutPort[JsValue]("out", "Forwarded packet")
  val outPorts = List(outPort)

  val instanceProps = Props(new ComponentActor(this) {
    val str = inPort.stream
      .withLatestFrom(delayPort.stream)((_, _))
      .flatMap { case (in, delay) => Observable.just(in).delay(delay.millis) }
      .pipeTo(outPort)
  })
}
