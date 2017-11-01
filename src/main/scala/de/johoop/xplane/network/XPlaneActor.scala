package de.johoop.xplane.network

import java.nio.channels.DatagramChannel

import akka.actor.{Actor, ActorRef, Props}
import de.johoop.xplane.network.SubscribingActor.SubscriberResponse
import de.johoop.xplane.network.UDPActor.EventRequest
import de.johoop.xplane.network.XPlaneActor.{Response, Subscribe, Unsubscribe}
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol.Payload

object XPlaneActor {
  type Event = Either[ProtocolError, Payload]

  sealed trait Message
  case class Response(event: Event) extends Message
  case class Subscribe(ref: ActorRef) extends Message
  case class Unsubscribe(ref: ActorRef) extends Message

  def props(channel: DatagramChannel): Props = Props(new XPlaneActor(channel))
}

class XPlaneActor(channel: DatagramChannel) extends Actor {
  UDPActor.create(channel)(context) ! EventRequest(self, channel)

  def receive: Receive = subscribedTo(Set.empty)

  def subscribedTo(subscribers: Set[ActorRef]): Receive = {
    case Response(event) =>
      subscribers foreach (_ ! SubscriberResponse(event))

    case Subscribe(ref) =>
      context.become(subscribedTo(subscribers + ref))

    case Unsubscribe(ref) =>
      context.become(subscribedTo(subscribers - ref))
  }
}
