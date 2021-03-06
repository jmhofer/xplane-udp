package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

import akka.pattern.{after, pipe}
import akka.Done
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Request._
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.network.protocol.Message._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.Future

object XPlaneServerMock {
  val payloadAddress = InetAddress getByName "localhost"
  val payloadPort = 49000

  val becn = BECN(1, 1, 1, 110501, 1, payloadPort, "xplane-server-mock")

  sealed trait Message
  case object Listen extends Message
  case object GetReceived extends Message
  case class Received(port: Int, request: Either[ProtocolError, Request]) extends Message
  case object ShutDown extends Message
  case class Broadcast(initialDelay: FiniteDuration = Duration.Zero) extends Message
  case class SendRREF(rref: RREF) extends Message
  case class SendRPOS(rpos: RPOS) extends Message
  case object ResetReceived extends Message

  def props(multicastGroup: InetAddress, multicastPort: Int): Props =
    Props(new XPlaneServerMock(multicastGroup, multicastPort))
}

class XPlaneServerMock(multicastGroup: InetAddress, multicastPort: Int) extends Actor with ActorLogging {
  import context.dispatcher
  import de.johoop.xplane.XPlaneServerMock._

  val multicastSocket = new DatagramSocket
  val payloadSocket = new DatagramSocket(new InetSocketAddress(payloadAddress, payloadPort))

  val receiveBuffer = ByteBuffer allocate 4096
  val receivePacket = new DatagramPacket(receiveBuffer.array, receiveBuffer.array.length)

  val bytes: Array[Byte] = becn.encode.array

  val becnPacket = new DatagramPacket(bytes, bytes.length, multicastGroup, multicastPort)

  self ! Listen

  def receive: Receive = receiveAndRemember(None, Vector.empty)

  def receiveAndRemember(maybePort: Option[Int], received: Vector[Either[ProtocolError, Request]]): Receive = {
    case Listen =>
      pipe(Future {
        receiveBuffer.clear
        payloadSocket.receive(receivePacket)
        Received(receivePacket.getPort, receiveBuffer.decode[Request])
      } (context.system.dispatchers.lookup("test-udp-dispatcher-receive"))) to self

    case Received(port, request) =>
      log debug s"received $request"
      context.become(receiveAndRemember(Option(port), received :+ request))
      self ! Listen

    case GetReceived =>
      log debug s"get received: $received"
      sender() ! received

    case Broadcast(initialDelay) =>
      after(initialDelay, context.system.scheduler)(Future {
        log debug "broadcasting"
        multicastSocket send becnPacket
        Done
      }) pipeTo sender()

    case SendRREF(rref) =>
      sendFromActor[RREF](maybePort, rref, sender())

    case SendRPOS(rpos) =>
      sendFromActor[RPOS](maybePort, rpos, sender())

    case ResetReceived =>
      context.become(receiveAndRemember(maybePort, Vector.empty))

    case ShutDown =>
      multicastSocket.close()
      payloadSocket.close()

    case Failure(_: ClosedChannelException) =>
      log debug "interrupted by shutdown"
      self ! PoisonPill
  }

  private def sendFromActor[T <: Payload](maybePort: Option[Int], t: T, sender: ActorRef)
                              (implicit enc: XPlaneEncoder[T]): Unit = maybePort match {
    case None => sender ! Done
    case Some(port) => send(port, t) pipeTo sender
  }

  def send[T <: Payload](port: Int, p: T)(implicit enc: XPlaneEncoder[T]): Future[Done] = Future {
    val bytes = p.encode.array
    log debug s"sending to $port"
    val packet = new DatagramPacket(bytes, bytes.length, payloadAddress, port)
    payloadSocket send packet
    Done
  } (context.system.dispatchers.lookup("test-udp-dispatcher-send"))
}
