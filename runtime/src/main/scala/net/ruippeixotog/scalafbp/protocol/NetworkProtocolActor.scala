package net.ruippeixotog.scalafbp.protocol

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import com.typesafe.config.Config

import net.ruippeixotog.scalafbp.protocol.message.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.GraphStore.GraphKey
import net.ruippeixotog.scalafbp.runtime._

class NetworkProtocolActor(graphStore: ActorRef, runtimeConfig: Config) extends AbstractProtocolActor[NetworkMessage] {
  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  private[this] val isDynamic = runtimeConfig.getBoolean("dynamic-networks")

  private[this] class OutputProxyActor(val inner: ActorRef) extends Actor {
    context.watch(inner)

    def receive = {
      case activity: NetworkBroker.Activity => inner ! activity.toMessage
      case cmd: NetworkBroker.NodeCommand => inner ! cmd.toMessage
      case error: NetworkBroker.NodeError => inner ! error.toMessage
      case error: NetworkBroker.NetworkError => inner ! error.toMessage

      case finished: NetworkController.Finished => inner ! finished.toMessage

      case Terminated(`inner`) => context.stop(self)

      case msg => log.warn(s"Cannot proxy unexpected message $msg to client")
    }
  }

  private[this] val proxyActorCache = mutable.Map[ActorRef, ActorRef]() // TODO change this to a proper cache

  var controllerActors = Map[String, ActorRef]()

  def controllerActorFor(id: String) = controllerActors.get(id) match {
    case Some(ref) => ref
    case None =>
      val sanitizedId = id.replaceAll("[^0-9A-Za-z]", "_")
      // TODO this actor is never terminated, as well as the graph store hook
      val ref = context.actorOf(Props(new NetworkController(id, isDynamic)), s"g-$sanitizedId-controller")
      if (isDynamic) graphStore ! Store.Watch(id, ref)
      controllerActors += id -> ref
      ref
  }

  def getGraph(id: String) =
    (graphStore ? Store.Get(GraphKey(id)))
      .mapTo[Store.Got[_, Graph]]
      .map(_.entity)

  def pipeStatusTo(controllerActor: ActorRef, to: ActorRef, toMessage: NetworkController.Status => NetworkMessage) =
    (controllerActor ? NetworkController.GetStatus).mapTo[NetworkController.Status].map(toMessage).pipeTo(to)

  def receiveMessage = {
    case payload: GetStatus =>
      val replyTo = sender()
      getGraph(payload.graph).map {
        case Some(_) => pipeStatusTo(controllerActorFor(payload.graph), replyTo, _.toStatusMessage)
        case None => replyTo ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Start =>
      val replyTo = sender()
      getGraph(payload.graph).map {
        case Some(graph) =>
          val outputActor = proxyActorCache.getOrElseUpdate(
            replyTo,
            context.actorOf(Props(new OutputProxyActor(replyTo))))

          val controllerActor = controllerActorFor(payload.graph)
          controllerActor ! NetworkController.Start(graph, outputActor)
          pipeStatusTo(controllerActor, replyTo, _.toStartedMessage())

        case None => replyTo ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Stop =>
      val replyTo = sender()
      getGraph(payload.graph).map {
        case Some(_) =>
          val controllerActor = controllerActorFor(payload.graph)
          controllerActor ! NetworkController.Stop
          pipeStatusTo(controllerActor, replyTo, _.toStoppedMessage())

        case None => replyTo ! Error(s"Graph ${payload.graph} not found")
      }

    case payload: Debug =>
      sender() ! payload.copy(enable = false)
  }
}
