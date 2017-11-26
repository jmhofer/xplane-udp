package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.johoop.xplane.network.UDPActor.{EventRequest, EventResponse}
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol.Payload
import akka.pattern.pipe
import de.johoop.xplane.network.XPlaneClientActor.{Event, Response}
import de.johoop.xplane.network.protocol.Response.ResponseDecoder

import scala.concurrent.{ExecutionContext, Future}

object UDPActor {
  sealed trait Message
  case class EventRequest(from: ActorRef, channel: DatagramChannel) extends Message
  case class EventResponse(request: EventRequest, event: Event) extends Message

  def props(maxResponseSize: Int): Props = Props(new UDPActor(maxResponseSize))
}

class UDPActor(maxResponseSize: Int) extends Actor with ActorLogging {
  import context.dispatcher

  // DANGER ZONE!
  // only safe because event requests get only sent once from the outside
  private val response = ByteBuffer allocate maxResponseSize

  def receive: Receive = {
    case request: EventRequest =>
      pipe(Future {
        log debug "listening..."
        response.clear

        request.channel receive response
        log debug "received response!"

        EventResponse(request, response.decode[Payload])
      } (ExecutionContext fromExecutor Executors.newSingleThreadExecutor)).to(self)

    case EventResponse(request, event) =>
      log debug s"received: $event"
      request.from ! Response(event)
      self ! request
  }
}
