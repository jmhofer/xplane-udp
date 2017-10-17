package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.util.concurrent.Executors

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import de.johoop.xplane.network.XPlaneSource.Event
import de.johoop.xplane.network.protocol.Payload
import de.johoop.xplane.network.protocol.Message._

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

object XPlaneSource {
  type Event = Either[DecodingError, Payload]

  def forClient(client: XPlaneClient, maxQueueSize: Int = 256, maxResponseSize: Int = 1024): Source[Event, NotUsed] =
    Source.fromGraph(new XPlaneSource(client, maxQueueSize, maxResponseSize))
}

class XPlaneSource(client: XPlaneClient, maxQueueSize: Int, maxResponseSize: Int) extends GraphStage[SourceShape[Event]] {
  val out: Outlet[Event] = Outlet("XPlaneSource")

  override val shape: SourceShape[Event] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var queue = Queue.empty[Event]

    override def preStart: Unit = {
      super.preStart

      val receiveCallback = getAsyncCallback[Event] { event =>
        if (queue.size < maxQueueSize) queue = queue enqueue event // if downstream doesn't keep up, events will simply be dropped
        pushIfAvailable
      }

      Future {
        val response = ByteBuffer allocate maxResponseSize
        while (!isClosed(out)) {
          response.clear
          client.channel receive response
          val event = response.decode[Payload]
          receiveCallback invoke event
        }
      } (ExecutionContext fromExecutor Executors.newSingleThreadExecutor)
    }

    setHandler(out, new OutHandler {
      override def onPull: Unit = pushIfAvailable
    })

    override def postStop: Unit = {
      // TODO clean up: unregister for events
      super.postStop
    }

    private def pushIfAvailable: Unit = {
      if (isAvailable(out)) {
        queue.dequeueOption foreach { case (payload, newQueue) =>
          queue = newQueue
          push(out, payload)
        }
      }
    }
  }
}
