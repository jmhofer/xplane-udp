package de.johoop.xplane.api

import java.net.{InetAddress, SocketAddress}
import java.nio.channels.{ClosedChannelException, DatagramChannel}

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import de.johoop.xplane.api.XPlaneActor.{Subscribe, Unsubscribe, UnsubscribeAll}
import de.johoop.xplane.network
import de.johoop.xplane.network.{XPlaneClientActor, XPlaneConnection, XPlaneSource}
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.network.protocol.Request._

import scala.concurrent.{ExecutionContext, Future}

case class XPlane private[xplane] (
  private[xplane] val address: SocketAddress,
  private[xplane] val channel: DatagramChannel,
  private[xplane] val actor: ActorRef,
  private[xplane] val registeredDataRefs: String Map Int = Map.empty,
  private[xplane] val nextDataRefIndex: Int = 0)

object XPlaneApi {
  def connect(multicastGroupName: String = "239.255.1.1", multicastPort: Int = 49707)(implicit system: ActorSystem, ec: ExecutionContext, timeout: Timeout): Future[ConnectedXPlaneApi] =
    network.createXPlaneClient(InetAddress getByName multicastGroupName, multicastPort) map (new ConnectedXPlaneApi(_))
}

// TODO maybe also handle RPOS
class ConnectedXPlaneApi(connection: XPlaneConnection)
                        (implicit system: ActorSystem, ec: ExecutionContext, timeout: Timeout) {
  private val xplane: ActorRef = system.actorOf(XPlaneActor.props(connection), "xplane")

  val beacon: BECN = connection.beacon

  def subscribeToDataRefs(frequency: Int, dataRefPaths: String*): Source[String Map Float, NotUsed] =
    Source.fromFuture(xplane.ask(Subscribe(frequency, dataRefPaths)).mapTo[Source[String Map Float, NotUsed]]).flatMapConcat(identity)

  def unsubscribeFromDataRefs(dataRefPaths: String*): Future[Done] = xplane.ask(Unsubscribe(dataRefPaths)).mapTo[Done]

  def getDataRef(dataRefPath: String)(implicit mat: Materializer): Future[Float] = {
    val dataRefs = subscribeToDataRefs(100, dataRefPath).toMat(Sink.head)(Keep.right).run()
    dataRefs map (_(dataRefPath)) andThen { case _ => unsubscribeFromDataRefs(dataRefPath) }
  }

  def setDataRef(dataRef: String, value: Float): Future[Done] = Future {
    network.sendTo(connection)(DREFRequest(value, dataRef))
    Done
  }

  def disconnect: Future[Done] = xplane ? UnsubscribeAll map { _ =>
    xplane ! PoisonPill
    connection.channel.close
    Done
  }
}

class XPlaneActor(connection: XPlaneConnection) extends Actor with ActorLogging {
  val clientActor: ActorRef = context.actorOf(XPlaneClientActor.props(connection.channel, maxResponseSize = 4096), "xplane-client")

  def receive: Receive = receiveWithDataRefs(nextDataRefIndex = 1, subscribedDataRefs = Map.empty)

  def receiveWithDataRefs(nextDataRefIndex: Int, subscribedDataRefs: String Map Int): Receive = {
    case Subscribe(frequency, dataRefs) =>
      log debug s"subscribe($frequency, $dataRefs)"
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
      log debug s"unsubscribe($dataRefs)"
      val unsubscribeFrom = subscribedDataRefs filter { case (path, _) => dataRefs contains path }

      unsubscribeFrom map { case (path, idx) =>
        RREFRequest(frequency = 0, idx, path)
      } foreach network.sendTo(connection)

      context become receiveWithDataRefs(nextDataRefIndex, subscribedDataRefs -- unsubscribeFrom.keys)

      sender() ! Done

    case UnsubscribeAll =>
      subscribedDataRefs map { case (path, idx) =>
        RREFRequest(frequency = 0, idx, path)
      } foreach { req =>
        try network.sendTo(connection)(req)
        catch { case _: ClosedChannelException =>
          log warning "trying to unsubscribe from already closed channel"
        }
      }

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
