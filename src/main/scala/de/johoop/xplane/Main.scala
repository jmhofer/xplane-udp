package de.johoop.xplane

import java.nio.ByteBuffer

import de.johoop.xplane.network.Message._
import de.johoop.xplane.network.Response._
import de.johoop.xplane.network._

object Main {
  def main(args: Array[String]): Unit = {

    val becn = resolveLocalXPlaneBeacon
    println(s"got: $becn")

    withXPlaneClient(doStuff)
  }

  def doStuff(client: XPlaneClient): Unit = {
    val send = sendTo(client)

    send(RPOSRequest(1))

    val dataRefs = Map(
      42 -> "sim/flightmodel/weight/m_fixed",
      43 -> "sim/flightmodel/weight/m_total",
      44 -> "sim/flightmodel/weight/m_fuel[0]",
      45 -> "sim/flightmodel/weight/m_fuel[1]",
      46 -> "sim/aircraft/overflow/acf_num_tanks",
      47 -> "sim/aircraft/weight/acf_m_fuel_tot")

    dataRefs foreach { case (id, path) => send(RREFRequest(1, id, path)) }

    val response = ByteBuffer.allocate(1024)

    1 to 5 foreach { _ =>
      response.clear
      client.channel receive response
      println(response.decode[Payload])
    }

    send(DREFRequest(153.0f, "sim/flightmodel/weight/m_fuel[0]"))
    send(DREFRequest(0.0f, "sim/flightmodel/weight/m_fuel[1]"))
    send(ALRTRequest(Vector("hello", "one", "two", "three")))

    1 to 5 foreach { _ =>
      response.clear
      client.channel receive response
      println(response.decode[Payload])
    }
  }
}
