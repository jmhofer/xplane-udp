package de.johoop.xplane.network

import akka.actor.{ActorContext, ActorRef, PoisonPill}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.johoop.xplane.network.XPlaneClientActor.{Event, Unsubscribe}
import de.johoop.xplane.network.protocol.Payload

class XPlaneSource(xplane: ActorRef)(implicit context: ActorContext) extends GraphStage[SourceShape[Payload]] {
  val out: Outlet[Payload] = Outlet("XPlaneSource")

  override val shape: SourceShape[Payload] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var value = Option.empty[Event]
    private var subscribingActor = Option.empty[ActorRef]

    override def preStart: Unit = {
      super.preStart

      val receiveCallback = getAsyncCallback[Event] { event =>
        value = Some(event) // always drop and replace older events
        pushIfAvailable
      }

      subscribingActor = Some(context.actorOf(SubscribingActor.props(xplane, receiveCallback)))
    }

    override def postStop: Unit = {
      subscribingActor foreach  { subscriber =>
        xplane ! Unsubscribe(subscriber)
        subscriber ! PoisonPill
      }
    }

    setHandler(out, new OutHandler {
      override def onPull: Unit = {
        pushIfAvailable
      }
    })

    private def pushIfAvailable: Unit = {
      if (isAvailable(out)) {
        value foreach { event =>
          value = None

          event match {
            case Left(error)    => fail(out, error)
            case Right(payload) => push(out, payload)
          }
        }
      }
    }
  }
}
