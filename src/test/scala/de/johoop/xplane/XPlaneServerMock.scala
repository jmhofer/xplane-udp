package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer

import akka.pattern.after
import akka.Done
import akka.actor.ActorSystem
import de.johoop.xplane.network.{multicastGroup, multicastPort}
import de.johoop.xplane.network.protocol.{BECN, Payload, XPlaneEncoder}
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.network.protocol.Message._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

class XPlaneServerMock(implicit ec: ExecutionContext) {
  val multicastSocket = new DatagramSocket

  val payloadAddress = InetAddress getByName "localhost"
  val payloadPort = 49000
  val payloadSocket = new DatagramSocket(new InetSocketAddress(payloadAddress, payloadPort))

  val receiveBuffer = ByteBuffer allocate 4096
  val receivePacket = new DatagramPacket(receiveBuffer.array, receiveBuffer.array.length)

  val becn = BECN(1, 1, 1, 110501, 1, payloadPort, "xplane-server-mock")
  val bytes: Array[Byte] = becn.encode.array

  val becnPacket = new DatagramPacket(bytes, bytes.length, multicastGroup, multicastPort)

  Future {
    receiveBuffer.clear
    payloadSocket.receive(receivePacket)
    println("Server Mock: received a packet from: " + receivePacket.getSocketAddress)
  }

  def broadcast(initialDelay: FiniteDuration = Duration.Zero)(implicit system: ActorSystem): Future[Done] =
    after(initialDelay, system.scheduler)(Future {
      multicastSocket send becnPacket
      Done
    } (system.dispatcher))

  def send[T <: Payload](p: T)(implicit enc: XPlaneEncoder[T]): Unit = {
    val bytes = p.encode.array
    val packet = new DatagramPacket(bytes, bytes.length, payloadAddress, receivePacket.getPort)
    println("Server Mock: sending to " + receivePacket.getSocketAddress)
    payloadSocket send packet
  }

  def shutdown: Unit = {
    multicastSocket.close
    payloadSocket.close
  }
}
