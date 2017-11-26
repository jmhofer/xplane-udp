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
  case class XPlaneConnection(channel: DatagramChannel, address: SocketAddress, beacon: BECN)

  private[xplane] def createXPlaneClient(multicastGroup: InetAddress, multicastPort: Int)(implicit ec: ExecutionContext): Future[XPlaneConnection] =
    resolveLocalXPlaneBeacon(multicastGroup, multicastPort) map { beacon =>
      val address = localXPlaneAddress(beacon)
      val channel = returning(DatagramChannel.open) { _ bind localAddress(0) }
      XPlaneConnection(channel, address, beacon)
    }

  private[xplane] def sendTo[T <: Request](connection: XPlaneConnection)(request: T)(implicit enc: XPlaneEncoder[T]): Unit = {
    connection.channel.send(request.encode, connection.address)
  }

  private[network] def localXPlaneAddress(becn: BECN): SocketAddress = localAddress(becn.port)

  private[network] def localAddress(port: Int): SocketAddress =
    new InetSocketAddress(InetAddress.getByName("localhost"), port)

  private[xplane] def resolveLocalXPlaneBeacon(multicastGroup: InetAddress, multicastPort: Int): Future[BECN] = Future {
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
