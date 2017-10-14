package de.johoop.xplane

import java.net.{DatagramPacket, InetAddress, InetSocketAddress, MulticastSocket}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import de.johoop.xplane.network.Message._
import de.johoop.xplane.network.Request._
import de.johoop.xplane.network.Response._
import de.johoop.xplane.network._
import de.johoop.xplane.util.returning

object Main {
  def main(args: Array[String]): Unit = {

    val xplaneAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), findXPlaneSocketViaMulticast.right.get)

    val channel = DatagramChannel.open
    channel bind null

    channel.send(RPOSRequest(1).encode, xplaneAddress)

    val dataRefs = Map(
      42 -> "sim/flightmodel/weight/m_fixed",
      43 -> "sim/flightmodel/weight/m_total",
      44 -> "sim/flightmodel/weight/m_fuel[0]",
      45 -> "sim/flightmodel/weight/m_fuel[1]",
      46 -> "sim/aircraft/overflow/acf_num_tanks",
      47 -> "sim/aircraft/weight/acf_m_fuel_tot")

    dataRefs foreach { case (id, path) => channel.send(RREFRequest(1, id, path).encode, xplaneAddress) }

    val response = ByteBuffer.allocate(1024)

    1 to 5 foreach { _ =>
      response.clear
      channel receive response
      println(response.decode[Payload])
    }

    channel.send(DREFRequest(153.0f, "sim/flightmodel/weight/m_fuel[0]").encode, xplaneAddress)
    channel.send(DREFRequest(0.0f, "sim/flightmodel/weight/m_fuel[1]").encode, xplaneAddress)
    channel.send(ALRTRequest(Vector("hello", "one", "two", "three")).encode, xplaneAddress)

    1 to 5 foreach { _ =>
      response.clear
      channel receive response
      println(response.decode[Payload])
    }

    channel.close
  }

  def findXPlaneSocketViaMulticast: Either[DecodingError, Int] = {
    val socket = new MulticastSocket(49707)
    val buf = try {
      socket.joinGroup(InetAddress.getByName("239.255.1.1"))
      returning(ByteBuffer.allocate(1024)) { b => socket.receive(new DatagramPacket(b.array, b.array.length)) }
    } finally socket.close

    buf.decode[Payload].map(_.asInstanceOf[BECN].port)
  }

  def encode[T](t: T)(implicit enc: XPlaneEncoder[T]): ByteBuffer = enc encode t
}

