package net.ruippeixotog.scalafbp.ws

import akka.NotUsed
import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }

import net.ruippeixotog.scalafbp.ws.SubscriptionManagerActor._

class SubscriptionManagerActor(domainActor: ActorRef) extends Actor with ActorLogging {
  private[this] var subscribers = Map.empty[String, ActorRef]

  def receive = {
    case Subscribe(id, ref) =>
      log.info("New client connected: {}", id)
      subscribers += id -> ref

    case Unsubscribe(id) =>
      log.info("Client disconnected: {}", id)
      subscribers -= id

    case ClientMessage(id, msg) =>
      subscribers.get(id).foreach(domainActor.tell(msg, _))
  }
}

object SubscriptionManagerActor {
  case class ClientMessage(id: String, msg: Any)
  case class Subscribe(id: String, ref: ActorRef)
  case class Unsubscribe(id: String)

  def subscriptionFlow[In, Out](id: String, managerActor: ActorRef): Flow[In, Out, NotUsed] = {

    // Sink to which client messages are sent. They are sent to `managerActor` wrapped in a `ClientMessage` with the
    // client ID.
    // When this sink is closed, an `Unsubscribe` message is sent to the `managerActor`.
    val in = Sink.actorRef[ClientMessage](managerActor, Unsubscribe(id))
      .contramap[In](ClientMessage(id, _))

    // Source that emits messages for clients. A new actor is materialized for each client. Messages sent to that actor
    // are flowed to the output end of this source.
    // The `managerActor` is notified with a `Subscribe` message when the actor is created.
    val out = Source.actorRef[Out](1000, OverflowStrategy.fail)
      .mapMaterializedValue(managerActor ! Subscribe(id, _))

    // Wrap the source and sink defined above into a two-ended flow
    Flow.fromSinkAndSource(in, out)
  }
}
