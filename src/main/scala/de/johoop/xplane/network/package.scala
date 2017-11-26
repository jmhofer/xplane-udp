package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors

import cats.implicits._
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.util.returning

import scala.concurrent.{ExecutionContext, Future}

package object network {
  private[xplane] val multicastGroup = InetAddress getByName "239.255.1.1"
  private[xplane] val multicastPort = 49707

  case class XPlaneConnection(channel: DatagramChannel, address: SocketAddress, beacon: BECN)

  private[xplane] def createXPlaneClient(implicit ec: ExecutionContext): Future[XPlaneConnection] =
    resolveLocalXPlaneBeacon map { beacon =>
      val address = localXPlaneAddress(beacon)
      val channel = returning(DatagramChannel.open) { ch =>
        ch bind new InetSocketAddress("localhost", 0)
        ch connect new InetSocketAddress("localhost", 49000)
      }
      XPlaneConnection(channel, address, beacon)
    }

  private[xplane] def sendTo[T <: Request](connection: XPlaneConnection)(request: T)(implicit enc: XPlaneEncoder[T]): Unit = {
    connection.channel.send(request.encode, connection.address)
  }

  private[network] def localXPlaneAddress(becn: BECN): SocketAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), becn.port)

  private[xplane] def resolveLocalXPlaneBeacon: Future[BECN] = Future {
    val socket = new MulticastSocket(multicastPort)
    val buf = try {
      socket joinGroup multicastGroup
      returning(ByteBuffer.allocate(1024)) { b => socket.receive(new DatagramPacket(b.array, b.array.length)) }
    } finally socket.close

    buf.decode[Payload] flatMap {
      case becn: BECN => Right(becn)
      case other => throw ProtocolError(s"expected a BECN, but got: $other")
    } valueOr { throw _ }
  } (ExecutionContext fromExecutor Executors.newSingleThreadExecutor)
}
