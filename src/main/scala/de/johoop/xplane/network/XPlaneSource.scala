package de.johoop.xplane.network

import akka.actor.{ActorContext, ActorRef, PoisonPill}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.johoop.xplane.network.XPlaneClientActor.{Event, Unsubscribe}
import de.johoop.xplane.network.protocol.Payload

import scala.collection.immutable.Queue

class XPlaneSource(xplane: ActorRef, maxQueueSize: Int = 256)(implicit context: ActorContext) extends GraphStage[SourceShape[Payload]] {
  val out: Outlet[Payload] = Outlet("XPlaneSource")

  override val shape: SourceShape[Payload] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var queue = Queue.empty[Event]
    private var subscribingActor = Option.empty[ActorRef]

    override def preStart: Unit = {
      super.preStart

      val receiveCallback = getAsyncCallback[Event] { event =>
        if (queue.size < maxQueueSize) queue = queue enqueue event // if downstream doesn't keep up, events will simply be dropped
        pushIfAvailable
      }

      println("prestart")
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
        println("onPull")
        pushIfAvailable
      }
    })

    private def pushIfAvailable: Unit = {
      if (isAvailable(out)) {
        queue.dequeueOption foreach { case (event, newQueue) =>
          queue = newQueue

          println(s"source got event: $event")

          event match {
            case Left(error)    => fail(out, error)
            case Right(payload) =>
              println(s"pushing: $payload!")
              push(out, payload)
          }
        }
      }
    }
  }
}
