package de.johoop.xplane.api

import java.net.SocketAddress
import java.nio.channels.DatagramChannel

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import cats.data.State
import de.johoop.xplane.network
import de.johoop.xplane.network.XPlaneSource
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Request._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class XPlane private[xplane] (
  private[xplane] val address: SocketAddress,
  private[xplane] val channel: DatagramChannel,
  private[xplane] val actor: ActorRef,
  private[xplane] val registeredDataRefs: String Map Int = Map.empty,
  private[xplane] val nextDataRefIndex: Int = 0)

// TODO maybe also handle RPOS
object XPlane {
  def subscribeToDataRefs(frequency: Int, dataRefs: String*)(implicit system: ActorSystem): State[XPlane, Source[String Map Float, NotUsed]] = for {
    xplane <- State.get[XPlane]
    currentIndex = xplane.nextDataRefIndex
    newlyRegistered = dataRefs zip (Stream from currentIndex)
    _ = newlyRegistered foreach { case (ref, idx) =>
      network.sendTo(xplane)(RREFRequest(frequency, idx, ref))
    }
    _ = State set xplane.copy(
          registeredDataRefs = xplane.registeredDataRefs ++ newlyRegistered,
          nextDataRefIndex = currentIndex + newlyRegistered.size)
  } yield Source
    .fromGraph(new XPlaneSource(xplane.actor))
    .collect { case RREF(receivedRefs) =>
      receivedRefs flatMap { case (idx, value) =>
        newlyRegistered.find(_._2 == idx).map { path => (path._1, value) }
      }
    }
    .filter(_.nonEmpty)

  def unsubscribeFromDataRefs(dataRefs: String*): State[XPlane, Unit] = for {
    xplane <- State.get[XPlane]
    unsubscribeFrom = xplane.registeredDataRefs filter { case (path, _) => dataRefs contains path }
    _ = unsubscribeFrom map { case (path, idx) => RREFRequest(frequency = 0, idx, path) } foreach network.sendTo(xplane)
    _ = State set xplane.copy(
      registeredDataRefs = xplane.registeredDataRefs -- unsubscribeFrom.keys
    )
  } yield ()

  def getDataRef(dataRef: String)(implicit system: ActorSystem, mat: Materializer): State[XPlane, Future[Float]] = for {
    source <- subscribeToDataRefs(100, dataRef)
    value = source .map (_ get dataRef) .collect { case Some(value) => value } .runWith(Sink.head)
    _ <- unsubscribeFromDataRefs(dataRef)
  } yield value

  def setDataRef(dataRef: String, value: Float): State[XPlane, Unit] = for {
    xplane <- State.get[XPlane]
    _ = network.sendTo(xplane)(DREFRequest(value, dataRef))
  } yield ()

  def run[A](plan: State[XPlane, A])(implicit system: ActorSystem, ec: ExecutionContext): Future[A] = network.withXPlane({ xplane =>
    val (updated, a) = plan.run(xplane).value
    updated.registeredDataRefs map { case (path, idx) =>  RREFRequest(frequency = 0, idx, path) } foreach network.sendTo(updated) // deregister from X-Plane
    a
  }, { case NonFatal(e) => throw e })
}
