package de.johoop.xplane

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import cats.implicits._
import de.johoop.xplane.api.XPlane
import de.johoop.xplane.network.protocol.Message._
import de.johoop.xplane.network.protocol.{BECN, Payload, RREFRequest, Request}
import de.johoop.xplane.util.returning

import scala.concurrent.{ExecutionContext, Future}

package object network {
  private[xplane] def withXPlane[T](op: XPlane => T, onError: ProtocolError => T = { e => throw ProtocolError(s"protocol has failed: $e") })
                                   (implicit ec: ExecutionContext): T =
    createXPlaneClient map { xplane =>
      try op(xplane) finally {
        xplane.registeredDataRefs foreach { case (path, index) =>
          network.sendTo(xplane)(RREFRequest(0, index, path)) // clean up udp subscriptions
        }
        xplane.channel.close
      }
    } recover onError

  private def createXPlaneClient: Future[XPlane] = resolveLocalXPlaneBeacon map localXPlaneAddress map { address =>
    val channel = returning(DatagramChannel.open)(_ bind null)
    XPlane(address, channel, XPlaneActor.props(channel))
  }

  private[xplane] def sendTo[T <: Request](client: XPlane)(request: T): Unit = client.channel.send(request.encode, client.address)

  private[network] def localXPlaneAddress(becn: BECN): SocketAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), becn.port)

  private[xplane] def resolveLocalXPlaneBeacon(implicit ec: ExecutionContext): Future[BECN] = Future {
    val socket = new MulticastSocket(49707)
    val buf = try {
      socket.joinGroup(InetAddress.getByName("239.255.1.1"))
      returning(ByteBuffer.allocate(1024)) { b => socket.receive(new DatagramPacket(b.array, b.array.length)) }
    } finally socket.close

    buf.decode[Payload] valueOr { other =>
      throw ProtocolError(s"expected a BECN response, but got: $other")
    }
  }
}
