package de.johoop.xplane.network

import java.nio.ByteBuffer
import java.util.concurrent.Executors

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import de.johoop.xplane.network.protocol.Payload
import de.johoop.xplane.network.protocol.Message._

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

class XPlaneSource(client: XPlaneClient, maxQueueSize: Int = 256, maxResponseSize: Int = 1024) extends GraphStage[SourceShape[Either[DecodingError, Payload]]] {
  val out: Outlet[Either[DecodingError, Payload]]] = Outlet("XPlaneSource")

  override val shape: SourceShape[Either[DecodingError, Payload]]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    implicit val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor)

    // TODO think about concurrency
    @volatile var queue = Queue.empty[Either[DecodingError, Payload]]]

    Future {
      val response = ByteBuffer allocate maxResponseSize
      while (!isClosed(out)) {
        response.clear
        client.channel receive response
        if (queue.size < maxQueueSize) {
          queue = queue enqueue response.decode[Payload] // if downstream doesn't keep up, events will simply be dropped
        }
        if (isAvailable(out)) { // TODO nope, has to go into a callback
          val (payload, newQueue) = queue.dequeue
          queue = newQueue
          push(out, payload)
        }
      }
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        // push(out, t)
        ???
      }

      override def onDownstreamFinish(): Unit = {
        // TODO clean up: unregister for events
        super.onDownstreamFinish()
      }
    })
  }
}
