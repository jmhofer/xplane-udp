package de.johoop.xplane.api

import java.net.SocketAddress
import java.nio.channels.DatagramChannel

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.Timeout
import de.johoop.xplane.api.XPlaneActor.{Subscribe, Unsubscribe, UnsubscribeAll}
import de.johoop.xplane.network
import de.johoop.xplane.network.{XPlaneClientActor, XPlaneConnection, XPlaneSource}
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Request._
import de.johoop.xplane.util.returning

import scala.concurrent.{ExecutionContext, Future}

case class XPlane private[xplane] (
  private[xplane] val address: SocketAddress,
  private[xplane] val channel: DatagramChannel,
  private[xplane] val actor: ActorRef,
  private[xplane] val registeredDataRefs: String Map Int = Map.empty,
  private[xplane] val nextDataRefIndex: Int = 0)

object XPlaneApi {
  def connect(implicit system: ActorSystem, ec: ExecutionContext, timeout: Timeout): Future[ConnectedXPlaneApi] =
    network.createXPlaneClient map (new ConnectedXPlaneApi(_))
}

// TODO maybe also handle RPOS
class ConnectedXPlaneApi(connection: XPlaneConnection)
                        (implicit system: ActorSystem, ec: ExecutionContext, timeout: Timeout) {
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

  def receive: Receive = receiveWithDataRefs(nextDataRefIndex = 0, subscribedDataRefs = Map.empty)

  def receiveWithDataRefs(nextDataRefIndex: Int, subscribedDataRefs: String Map Int): Receive = {
    case Subscribe(frequency, dataRefs) =>
      val newlySubscribed = dataRefs zip (Stream from nextDataRefIndex)

      newlySubscribed foreach { case (ref, idx) =>
        network.sendTo(connection)(RREFRequest(frequency, idx, ref))
      }

      context become receiveWithDataRefs(nextDataRefIndex + newlySubscribed.size, subscribedDataRefs ++ newlySubscribed)

      sender() ! Source
        .fromGraph(new XPlaneSource(clientActor))
        .collect { case RREF(receivedRefs) =>
          receivedRefs flatMap { case (idx, value) =>
            newlySubscribed.find(_._2 == idx).map { path => (path._1, value) }
          }
        }
        .filter(_.nonEmpty)

    case Unsubscribe(dataRefs) =>
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
  case class Subscribe(frequency: Int, dataRefs: Seq[String])
  case class Unsubscribe(dataRefs: Seq[String])
  case object UnsubscribeAll

  def props(connection: XPlaneConnection): Props = Props(new XPlaneActor(connection))
}
