package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, DatagramChannel}

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import de.johoop.xplane.network.UDPActor.{EventRequest, EventResponse, ShutDown}
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol.Payload
import akka.pattern.pipe
import akka.stream.stage.AsyncCallback
import de.johoop.xplane.network
import de.johoop.xplane.network.XPlaneSource.Event
import de.johoop.xplane.network.protocol.Response.ResponseDecoder

import scala.concurrent.Future

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

  def receive: Receive = {
    case EventRequest =>
      pipe(Future {
        log debug s"listening at ${channel.getLocalAddress}..."
        response.clear

        channel receive response
        log debug "received response!"

        EventResponse(response.decode[Payload])
      } (network.udpDispatcher(context.system))).to(self)

    case EventResponse(event) =>
      log debug s"received: $event"
      callback invoke event
      self ! EventRequest

    case ShutDown =>
      log debug "shutting down"
      channel.close() // this will cause the receive to interrupt, the respective failure is caught below

    case Failure(_: ClosedChannelException) =>
      log debug "interrupted by shutdown"
      self ! PoisonPill
  }
}
