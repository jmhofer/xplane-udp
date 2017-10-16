package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import de.johoop.xplane.network.Message._
import de.johoop.xplane.util.returning

package object network {
  case class XPlaneClient(address: SocketAddress, channel: DatagramChannel)

  def withXPlaneClient[T](op: XPlaneClient => T, onError: DecodingError => T = { e => throw new IllegalStateException(s"unable to decode: $e") }): T = {
    resolveLocalXPlaneBeacon.map(localXPlaneAddress).fold(onError, { address =>
      val channel = returning(DatagramChannel.open)(_ bind null)
      try op(XPlaneClient(address, channel)) finally channel.close
    })
  }

  def sendTo[T <: Request](client: XPlaneClient)(request: T): Unit = client.channel.send(request.encode, client.address)

  def localXPlaneAddress(becn: BECN): SocketAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), becn.port)

  def resolveLocalXPlaneBeacon: Either[DecodingError, BECN] = {
    val socket = new MulticastSocket(49707)
    val buf = try {
      socket.joinGroup(InetAddress.getByName("239.255.1.1"))
      returning(ByteBuffer.allocate(1024)) { b => socket.receive(new DatagramPacket(b.array, b.array.length)) }
    } finally socket.close

    buf.decode[Payload] flatMap {
      case becn: BECN => Right(becn)
      case other      => Left(DecodingError(s"expected a BECN response, but got: $other"))
    }
  }
}
