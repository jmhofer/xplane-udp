package de.johoop.xplane.api

import java.net.SocketAddress
import java.nio.channels.DatagramChannel

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import cats.data.State
import de.johoop.xplane.api.XPlaneActor.{Subscribe, Unsubscribe, UnsubscribeAll}
import de.johoop.xplane.network
import de.johoop.xplane.network.{XPlaneClientActor, XPlaneConnection, XPlaneSource}
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

object XPlaneApi {
  def connect(implicit system: ActorSystem, ec: ExecutionContext): Future[ConnectedXPlaneApi] = network.createXPlaneClient map (new ConnectedXPlaneApi(_))
}

// TODO maybe also handle RPOS
class ConnectedXPlaneApi(connection: XPlaneConnection)(implicit system: ActorSystem, ec: ExecutionContext) {
  private val xplane: ActorRef = system.actorOf(XPlaneActor.props(connection))

  val beacon: BECN = connection.beacon

  def subscribeToDataRefs(frequency: Int, dataRefs: String*): Source[String Map Float, NotUsed] =
    Source.fromFuture(xplane.ask(Subscribe(frequency, dataRefs)).mapTo[Source[String Map Float, NotUsed]]).flatMapConcat(identity)

  def unsubscribeFromDataRefs(dataRefs: String*): Future[Done] = xplane.ask(Unsubscribe(dataRefs)).mapTo[Done]

  def getDataRef(dataRef: String): Future[Float] = ??? // TODO implement
  def setDataRef(dataRef: String, value: Float): Future[Done] = ??? // TODO implement

  def disconnect: Future[Done] = returning(xplane.ask(UnsubscribeAll).mapTo[Done]) { _ => connection.channel.close }
}

class XPlaneActor(connection: XPlaneConnection) extends Actor {
  val clientActor: ActorRef = context.actorOf(XPlaneClientActor.props(connection.channel, maxResponseSize = 4096))

  def receive: Receive = receiveWithDataRefs(nextDataRefIndex = 0, registeredDataRefs = Map.empty)

  def receiveWithDataRefs(nextDataRefIndex: Int, subscribedDataRefs: String Map Int): Receive = {
    case Subscribe(frequency, dataRefs) =>
      val newlySubscribed = dataRefs zip (Stream from currentIndex)

      newlySubscribed foreach { case (ref, idx) =>
        network.sendTo(connection)(RREFRequest(frequency, idx, ref))
      }

      context become receiveWithDataRefs(nextDataRefIndex + newlySubscribed.size, subscribedDataRefs + newlySubscribed)

      sender() ! Source
        .fromGraph(new XPlaneSource(clientActor))
        .collect { case RREF(receivedRefs) =>
          receivedRefs flatMap { case (idx, value) =>
            newlySubscribed.find(_._2 == idx).map { path => (path._1, value) }
          }
        }
        .filter(_.nonEmpty)

    case Unsubscribe(dataRefs: Array[String]) =>
      val unsubscribeFrom = subscribedDataRefs filter { case (path, _) => dataRefs contains path }

      unsubscribeFrom map { case (path, idx) =>
        RREFRequest(frequency = 0, idx, path)
      } foreach network.sendTo(connection)

      context become receiveWithDataRefs(nextDataRefIndex, subscribedDataRefs -- unsubscribeFrom.keys)

      sender() ! Done

    case UnsubscribeAll =>
      subscribedDataRefs map { case (path, idx) =>
        RREFRequest(frequency = 0, idx, path)
      } foreach network.sendTo(connection)

      context become receive

      sender() ! Done
  }
}

object XPlaneActor {
  sealed trait Message
  case class Subscribe(frequency: Int, dataRefs: Array[String])
  case class Unsubscribe(dataRefs: Array[String])
  case object UnsubscribeAll

  def props(connection: XPlaneConnection): Props = Props(new XPlaneActor(connection))
}

// TODO maybe also handle RPOS
// TODO probably, state monad and streams are too incompatible - maybe, this should actually be an actor instead
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

  def close: State[XPlane, Unit] = for { // FIXME this won't do...
    xplane <- State.get[XPlane]
    _ <- unsubscribeFromDataRefs(xplane.registeredDataRefs)
    _ = xplane.channel.close
  } yield ()

  def run[A](plan: State[XPlane, A])(implicit system: ActorSystem, ec: ExecutionContext): Future[A] = {
    network.createXPlaneClient map { xplane =>
      val (updated, a) = plan.run(xplane).value
      a
    }
  }
}
