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

class ConnectedToXPlane(val beacon: BECN)(implicit system: ActorSystem, ec: ExecutionContext) {
  private val connectionForSending: XPlaneConnection = network.createXPlaneClient(beacon)

  def subscribeToRPOS(frequency: Int): Source[RPOS, NotUsed] = {
    def sendSubscription(frequency: Int)(connection: XPlaneConnection): Unit =
      network.sendTo(connection)(RPOSRequest(frequency))

    def convertToRPOS(payload: Payload): Option[RPOS] = payload match {
      case rpos: RPOS => Option(rpos)
      case other =>
        system.log.warning("received an unexpected response: {}", other)
        None
    }

    Source
      .fromGraph(new XPlaneSource(beacon, subscribe = sendSubscription(frequency), unsubscribe = sendSubscription(0)))
      .map(convertToRPOS)
      .collect { case Some(rpos) => rpos }
  }

  def subscribeToDataRefs(frequency: Int, dataRefPaths: String*): Source[String Map Float, NotUsed] = {
    val dataRefs: Vector[(String, Int)] = dataRefPaths.toVector zip (Stream from 1)

    def sendSubscriptionRequests(frequency: Int)(connection: XPlaneConnection): Unit =
    dataRefs foreach { case (path, id) => network.sendTo(connection)(RREFRequest(frequency, id, path)) }

    def convertToMap(payload: Payload): String Map Float = payload match {
      case RREF(dataRefValues) =>
        dataRefValues .flatMap { case (idToFind, value) =>
          dataRefs.collect { case (path, id) if id == idToFind => (path, value) }
        }

      case other =>
        system.log.warning("received an unexpected response: {}", other)
        Map.empty
    }

    Source
      .fromGraph(new XPlaneSource(
        beacon, subscribe = sendSubscriptionRequests(frequency), unsubscribe = sendSubscriptionRequests(0)))
      .map(convertToMap)
  }

  def getDataRef(dataRefPath: String)(implicit mat: Materializer): Future[Float] =
    subscribeToDataRefs(100, dataRefPath).toMat(Sink.head)(Keep.right).run() map (_(dataRefPath))

  def setDataRef(dataRef: String, value: Float): Future[Done] = Future {
    network.sendTo(connectionForSending)(DREFRequest(value, dataRef))
    Done
  }
}
