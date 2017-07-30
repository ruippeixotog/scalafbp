package net.ruippeixotog.scalafbp.protocol

import scala.reflect.ClassTag

import akka.actor.Actor
import akka.event.slf4j.SLF4JLogging

import net.ruippeixotog.scalafbp.protocol.message.Message

abstract class AbstractProtocolActor[M <: Message](implicit ct: ClassTag[M]) extends Actor with SLF4JLogging {

  private[this] def actorClassName = getClass.getSimpleName
  private[this] def messageClassName = ct.runtimeClass.getSimpleName

  def receiveMessage: PartialFunction[M, Unit]

  final def receive = {
    case msg: M =>
      receiveMessage.applyOrElse(msg, { _: M => log.warn(s"$actorClassName did not handle message $msg") })

    case msg =>
      log.warn(s"$actorClassName received $msg, which is not of type $messageClassName. The message will be discarded.")
  }
}
