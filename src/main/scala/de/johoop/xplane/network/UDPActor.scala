package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import de.johoop.xplane.network.UDPActor.{EventRequest, EventResponse, ShutDown}
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol.Payload
import akka.pattern.pipe
import akka.stream.stage.AsyncCallback
import de.johoop.xplane.network.XPlaneSource.Event
import de.johoop.xplane.network.protocol.Response.ResponseDecoder

import scala.concurrent.{ExecutionContext, Future}

object UDPActor {
  sealed trait Message
  case object ShutDown extends Message
  private[UDPActor] case object EventRequest extends Message
  private[UDPActor] final case class EventResponse(event: Event) extends Message

  def props(channel: DatagramChannel, callback: AsyncCallback[Event], maxResponseSize: Int) =
    Props(new UDPActor(channel, callback, maxResponseSize))
}

class UDPActor(channel: DatagramChannel, callback: AsyncCallback[Event], maxResponseSize: Int) extends Actor with ActorLogging {
  import context.dispatcher

  // only safe because this actor doesn't receive any requests from the outside!
  private val response = ByteBuffer allocate maxResponseSize

  self ! EventRequest

  def receive: Receive = receiveWhileActive(true)

  def receiveWhileActive(active: Boolean): Receive = {
    case ShutDown =>
      channel.close()
      context become receiveWhileActive(active = false)

    case EventRequest =>
      pipe(Future {
        log debug s"listening at ${channel.getLocalAddress}..."
        response.clear

        channel receive response
        log debug "received response!"

        EventResponse(response.decode[Payload])
      } (ExecutionContext fromExecutor Executors.newSingleThreadExecutor)).to(self)

    case EventResponse(event) =>
      log debug s"received: $event"
      if (active) {
        callback invoke event
        self ! EventRequest
      } else self ! PoisonPill
  }
}
