package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import de.johoop.xplane.network.UDPActor.{EventRequest, EventResponse}
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol.Payload
import akka.pattern.pipe
import de.johoop.xplane.network.XPlaneActor.{Event, Response}

import scala.concurrent.{ExecutionContext, Future}

object UDPActor {
  sealed trait Message
  case class EventRequest(from: ActorRef, channel: DatagramChannel) extends Message
  case class EventResponse(request: EventRequest, event: Event) extends Message

  def props: Props = Props[UDPActor]

  def create(channel: DatagramChannel)(implicit context: ActorContext): ActorRef = {
    val xplane = context actorOf props
    xplane ! EventRequest(channel)
  }
}

class UDPActor extends Actor {
  // DANGER ZONE!
  // only safe because event requests get only sent once from the outside
  private val response = ByteBuffer allocate maxResponseSize

  def receive: Receive = {
    case request @ EventRequest(from, channel) =>
      pipe(Future {
        response.clear
        channel receive response
        EventResponse(request, response.decode[Payload])
      } (ExecutionContext fromExecutor Executors.newSingleThreadExecutor)).to(self)

    case EventResponse(request, event) =>
      request.from ! Response(event)
      self ! request
  }
}
