package net.ruippeixotog.scalafbp.protocol

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.{ GraphStore, NetworkBroker, NetworkController }

class NetworkProtocolActor(graphStore: GraphStore) extends AbstractProtocolActor[NetworkMessage] {
  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  private[this] class OutputProxyActor(val inner: ActorRef) extends Actor {
    def receive = {
      case output: ComponentActor.Output => inner ! output.toMessage
      case error: NetworkBroker.Error => inner ! error.toMessage
      case activity: NetworkBroker.Activity => inner ! activity.toMessage
      case finished: NetworkController.Finished => inner ! finished.toMessage
      case msg => log.warn(s"Cannot proxy unexpected message $msg to client")
    }
  }

  private[this] val proxyActorCache = mutable.Map[ActorRef, ActorRef]() // TODO change this to a proper cache

  var controllerActors = Map[String, ActorRef]()

  def controllerActorFor(id: String) = controllerActors.get(id) match {
    case Some(ref) => ref
    case None =>
      val ref = context.actorOf(Props(new NetworkController(id)), s"g-$id-controller")
      controllerActors += id -> ref
      ref
  }

  def pipeStatusToSender(controllerActor: ActorRef, toMessage: NetworkController.Status => NetworkMessage) =
    (controllerActor ? NetworkController.GetStatus).mapTo[NetworkController.Status].map(toMessage).pipeTo(sender())

  def receiveMessage = {
    case payload: GetStatus =>
      graphStore.get(payload.graph) match {
        case Some(_) => pipeStatusToSender(controllerActorFor(payload.graph), _.toStatusMessage)
        case None => sender() ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Start =>
      graphStore.get(payload.graph) match {
        case Some(graph) =>
          val messageOutputActor = sender()
          val outputActor = proxyActorCache.getOrElseUpdate(
            messageOutputActor, context.actorOf(Props(new OutputProxyActor(messageOutputActor))))

          val controllerActor = controllerActorFor(payload.graph)
          controllerActor ! NetworkController.Start(graph, outputActor)
          pipeStatusToSender(controllerActor, _.toStartedMessage())

        case None => sender() ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Stop =>
      graphStore.get(payload.graph) match {
        case Some(_) =>
          val controllerActor = controllerActorFor(payload.graph)
          controllerActor ! NetworkController.Stop
          pipeStatusToSender(controllerActor, _.toStoppedMessage())

        case None => sender() ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Debug =>
      sender() ! payload.copy(enable = false)
  }
}
