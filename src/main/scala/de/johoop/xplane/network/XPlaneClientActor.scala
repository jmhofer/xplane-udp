package de.johoop.xplane.network

import java.nio.channels.DatagramChannel

import akka.actor.{Actor, ActorRef, Props}
import de.johoop.xplane.network.SubscribingActor.SubscriberResponse
import de.johoop.xplane.network.UDPActor.EventRequest
import de.johoop.xplane.network.XPlaneClientActor.{Response, Subscribe, Unsubscribe}
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol.Payload

object XPlaneClientActor {
  type Event = Either[ProtocolError, Payload]

  sealed trait Message
  case class Response(event: Event) extends Message
  case class Subscribe(ref: ActorRef) extends Message
  case class Unsubscribe(ref: ActorRef) extends Message

  def props(channel: DatagramChannel, maxResponseSize: Int): Props = Props(new XPlaneClientActor(channel, maxResponseSize))
}

class XPlaneClientActor(channel: DatagramChannel, maxResponseSize: Int) extends Actor {
  context.actorOf(UDPActor.props(maxResponseSize)) ! EventRequest(self, channel)

  def receive: Receive = subscribedTo(Set.empty)

  def subscribedTo(subscribers: Set[ActorRef]): Receive = {
    case Response(event) =>
      subscribers foreach { _ ! SubscriberResponse(event) }

    case Subscribe(ref) =>
      context.become(subscribedTo(subscribers + ref))

    case Unsubscribe(ref) =>
      context.become(subscribedTo(subscribers - ref))
  }
}
