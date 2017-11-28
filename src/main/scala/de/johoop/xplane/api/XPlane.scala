package de.johoop.xplane.api

import java.net.InetAddress

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import de.johoop.xplane.network
import de.johoop.xplane.network.{XPlaneConnection, XPlaneSource}
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Request._

import scala.concurrent.{ExecutionContext, Future}

object XPlane {
  def connect(multicastGroupName: String = "239.255.1.1", multicastPort: Int = 49707)
             (implicit system: ActorSystem, ec: ExecutionContext): Future[ConnectedToXPlane] =
    network.resolveLocalXPlaneBeacon(InetAddress getByName multicastGroupName, multicastPort) map (new ConnectedToXPlane(_))
}

// TODO maybe also handle RPOS
class ConnectedToXPlane(val beacon: BECN)(implicit system: ActorSystem, ec: ExecutionContext) {
  private val connectionForSending: XPlaneConnection = network.createXPlaneClient(beacon)

  def subscribeToRPOS(frequency: Int): Source[RPOS, NotUsed] = Source.empty // TODO implement

  def subscribeToDataRefs(frequency: Int, dataRefPaths: String*): Source[String Map Float, NotUsed] =
    Source fromGraph new XPlaneSource(beacon, frequency, dataRefPaths.toVector)

  def getDataRef(dataRefPath: String)(implicit mat: Materializer): Future[Float] =
    subscribeToDataRefs(100, dataRefPath).toMat(Sink.head)(Keep.right).run() map (_(dataRefPath))

  def setDataRef(dataRef: String, value: Float): Future[Done] = Future {
    network.sendTo(connectionForSending)(DREFRequest(value, dataRef))
    Done
  }
}
