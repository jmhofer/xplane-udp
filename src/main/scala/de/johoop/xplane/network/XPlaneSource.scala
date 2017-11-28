package de.johoop.xplane.network

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.johoop.xplane.network
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol.{BECN, Payload, RREF, RREFRequest}

object XPlaneSource {
  val maxResponseSize = 8192

  type Event = Either[ProtocolError, Payload]
}

class XPlaneSource(beacon: BECN, frequency: Int, dataRefPaths: Vector[String])(implicit system: ActorSystem)
  extends GraphStage[SourceShape[String Map Float]] {

  import XPlaneSource._

  val connection: XPlaneConnection = network createXPlaneClient beacon
  val dataRefs: Vector[(String, Int)] = dataRefPaths zip (Stream from 1)

  val out: Outlet[String Map Float] = Outlet("XPlaneSource")

  override val shape: SourceShape[String Map Float] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var mostRecentEvent = Option.empty[Event]
    private var udpActor = Option.empty[ActorRef]

    override def preStart: Unit = {
      super.preStart

      val receiveCallback = getAsyncCallback[Event] { event =>
        mostRecentEvent = Some(event) // always drop and replace older events
        pushIfAvailable
      }

      udpActor = Some(system.actorOf(UDPActor.props(connection.channel, receiveCallback, maxResponseSize)))
      sendSubscriptionRequests(frequency)
    }

    override def postStop: Unit = {
      udpActor foreach  { udpActor =>
        udpActor ! PoisonPill
        sendSubscriptionRequests(frequency = 0)
        connection.channel.close()
      }
    }

    setHandler(out, new OutHandler {
      override def onPull: Unit = {
        pushIfAvailable
      }
    })

    private def pushIfAvailable: Unit = {
      if (isAvailable(out)) {
        mostRecentEvent foreach { event =>
          mostRecentEvent = None

          event match {
            case Left(error)    => fail(out, error)
            case Right(payload) =>
              push(out, convertToMap(payload))
          }
        }
      }
    }

    private def sendSubscriptionRequests(frequency: Int): Unit =
      dataRefs foreach { case (path, id) => network.sendTo(connection)(RREFRequest(frequency, id, path)) }

    private def convertToMap(payload: Payload): String Map Float = payload match {
      case RREF(dataRefValues) =>
        dataRefValues .flatMap { case (idToFind, value) =>
          dataRefs.collect { case (path, id) if id == idToFind => (path, value) }
        }

      case other =>
        system.log.warning("received an unexpected response: {}", other)
        Map.empty
    }
  }
}
