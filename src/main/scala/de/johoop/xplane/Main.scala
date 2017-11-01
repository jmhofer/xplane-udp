package de.johoop.xplane

import de.johoop.xplane.api.XPlane
import de.johoop.xplane.network._
import de.johoop.xplane.network.protocol._

object Main {
  def main(args: Array[String]): Unit = {
/*
    val becn = resolveLocalXPlaneBeacon
    println(s"got: $becn")

    withXPlaneClient(doStuff)
*/
  }

  def doStuff(client: XPlane): Unit = {
    // TODO rewrite with new API
    /*
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

    XPlaneSource forClient client take 5 runForeach println

    send(DREFRequest(153.0f, "sim/flightmodel/weight/m_fuel[0]"))
    send(DREFRequest(0.0f, "sim/flightmodel/weight/m_fuel[1]"))
    send(ALRTRequest(Vector("hello", "one", "two", "three")))

    XPlaneSource forClient client take 5 runForeach println
    */
  }
}
