package de.johoop.xplane.network

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.johoop.xplane.network
import de.johoop.xplane.network.UDPActor.ShutDown
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol.{BECN, Payload}

object XPlaneSource {
  val maxResponseSize = 8192

  type Event = Either[ProtocolError, Payload]
}

class XPlaneSource(
    beacon: BECN,
    subscribe: XPlaneConnection => Unit,
    unsubscribe: XPlaneConnection => Unit)(implicit system: ActorSystem)

  extends GraphStage[SourceShape[Payload]] {

  import XPlaneSource._

  val connection: XPlaneConnection = network createXPlaneClient beacon

  val out: Outlet[Payload] = Outlet("XPlaneSource")

  override val shape: SourceShape[Payload] = SourceShape(out)

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
      subscribe(connection)
    }

    override def postStop: Unit = {
      udpActor foreach  { udpActor =>
        unsubscribe(connection)
        udpActor ! ShutDown
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
            case Right(payload) => push(out, payload)
          }
        }
      }
    }
  }
}
