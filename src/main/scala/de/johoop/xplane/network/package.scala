package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.ActorSystem
import cats.implicits._
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.util.returning

import scala.concurrent.{ExecutionContext, Future}

package object network {
  case class XPlaneConnection(channel: DatagramChannel, address: SocketAddress)

  private[xplane] def createXPlaneClient(beacon: BECN): XPlaneConnection =
    XPlaneConnection(
      returning(DatagramChannel.open) { _ bind localAddress(0) },
      localXPlaneAddress(beacon))

  private[xplane] def sendTo[T <: Request](connection: XPlaneConnection)(request: T)(implicit enc: XPlaneEncoder[T]): Unit =
    connection.channel.send(request.encode, connection.address)

  private[network] def localXPlaneAddress(becn: BECN): SocketAddress = localAddress(becn.port)

  private[network] def localAddress(port: Int): SocketAddress =
    new InetSocketAddress(InetAddress.getByName("localhost"), port)

  private[xplane] def resolveLocalXPlaneBeacon(multicastGroup: InetAddress, multicastPort: Int)(implicit system: ActorSystem): Future[BECN] = Future {
    val socket = new MulticastSocket(multicastPort)
    val buf = try {
      socket joinGroup multicastGroup
      returning(ByteBuffer.allocate(1024)) { b => socket.receive(new DatagramPacket(b.array, b.array.length)) }
    } finally socket.close

    buf.decode[Payload] flatMap {
      case becn: BECN => Right(becn)
      case other => throw ProtocolError(s"expected a BECN, but got: $other")
    } valueOr { throw _ }
  } (udpDispatcher)

  def udpDispatcher(implicit system: ActorSystem): ExecutionContext = system.dispatchers.lookup("udp-dispatcher")
}
