package net.ruippeixotog.scalafbp.protocol

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout

import net.ruippeixotog.scalafbp.component.ComponentActor
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessage
import net.ruippeixotog.scalafbp.protocol.message.NetworkMessages._
import net.ruippeixotog.scalafbp.protocol.message.ToMessageConversions._
import net.ruippeixotog.scalafbp.runtime.GraphStore.GraphKey
import net.ruippeixotog.scalafbp.runtime.{ Graph, GraphStore, NetworkBroker, NetworkController }

class NetworkProtocolActor(graphStore: ActorRef) extends AbstractProtocolActor[NetworkMessage] {
  implicit val timeout = Timeout(3.seconds)
  implicit val ec = context.dispatcher

  private[this] class OutputProxyActor(val inner: ActorRef) extends Actor {
    context.watch(inner)

    def receive = {
      case output: ComponentActor.Output => inner ! output.toMessage
      case error: NetworkBroker.Error => inner ! error.toMessage
      case error: NetworkBroker.ProcessError => inner ! error.toMessage
      case activity: NetworkBroker.Activity => inner ! activity.toMessage
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
      val ref = context.actorOf(Props(new NetworkController(id)), s"g-$id-controller")
      controllerActors += id -> ref
      ref
  }

  def getGraph(id: String) =
    (graphStore ? GraphStore.Get(GraphKey(id)))
      .mapTo[GraphStore.Got[Graph]]
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
