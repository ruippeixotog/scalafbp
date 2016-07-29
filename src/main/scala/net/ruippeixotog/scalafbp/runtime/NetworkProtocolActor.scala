package net.ruippeixotog.scalafbp.runtime

import akka.actor.Actor

import net.ruippeixotog.scalafbp.protocol.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.{ Network => NetworkProtocol }

class NetworkProtocolActor extends Actor {
  var started = false

  def wrap(payload: Payload) = NetworkProtocol(payload)

  def receive = {
    case payload: GetStatus =>
      sender() ! wrap(Status(payload.graph, started, started, None, None))

    case payload: Start =>
      started = true
      sender() ! wrap(Started(payload.graph, System.currentTimeMillis(), started, started, None))

    case payload: Stop =>
      started = false
      sender() ! wrap(Stopped(payload.graph, System.currentTimeMillis(), started, started, None))

    case msg => println(s"UNHANDLED MESSAGE: $msg")
  }
}
