package de.johoop.xplane.network

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.stage.AsyncCallback
import de.johoop.xplane.network.SubscribingActor.SubscriberResponse
import de.johoop.xplane.network.XPlaneClientActor.{Event, Subscribe}

object SubscribingActor {
  sealed trait Message
  case class SubscriberResponse(event: Event) extends Message

  def props(xplane: ActorRef, callback: AsyncCallback[Event]): Props = Props(new SubscribingActor(xplane, callback))
}

class SubscribingActor(xplane: ActorRef, callback: AsyncCallback[Event]) extends Actor with ActorLogging {
  xplane ! Subscribe(self)

  def receive: Receive = {
    case SubscriberResponse(event) =>
      log debug s"subscribed: $event"
      callback.invoke(event)
  }
}



