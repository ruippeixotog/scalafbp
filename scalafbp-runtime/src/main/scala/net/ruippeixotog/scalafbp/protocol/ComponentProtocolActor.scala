package net.ruippeixotog.scalafbp.protocol

import scala.concurrent.duration._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import net.ruippeixotog.scalafbp.protocol.message.ComponentMessage
import net.ruippeixotog.scalafbp.protocol.message.ComponentMessages.{ List => ListComponents, _ }
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.ComponentRegistry._
import net.ruippeixotog.scalafbp.runtime.Store
import net.ruippeixotog.scalafbp.component

class ComponentProtocolActor(compRegistry: ActorRef, graphStore: ActorRef)
    extends AbstractProtocolActor[ComponentMessage] {

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  def receiveMessage = {
    case _: ListComponents =>
      val replyTo = sender()

      (compRegistry ? Store.GetAll(ComponentsKey)).mapTo[Store.GotAll[_, component.Component]].foreach {
        case Store.GotAll(_, comps) =>
          comps.foreach(replyTo ! _.toMessage)
          replyTo ! ComponentsReady(comps.size)
      }
  }
}
