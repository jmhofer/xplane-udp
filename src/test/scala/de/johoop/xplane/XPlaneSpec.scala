package de.johoop.xplane

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import de.johoop.xplane.api.{ConnectedXPlaneApi, XPlaneApi}
import de.johoop.xplane.network.protocol.RREF
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.util.returning
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.AfterAll

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class XPlaneSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers { def is = "XPlane".title ^ sequential ^ s2"""
  The XPlane client should work.

  The low-level network package must find a broadcasting X-Plane installation and return its beacon data.  $e1

  For the high-level X-Plane API,
    connecting and immediately disconnecting again must succeed,                         $e2
    connecting and retrieving the beacon must return the correct beacon information.     $e3
    subscribing to a dataref should provide us with a source of values for this dataref. $e4
  """

  implicit val system: ActorSystem = ActorSystem("xplane-spec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(1 second)

  import system.dispatcher

  val mock = new XPlaneServerMock()

  def afterAll: Unit = {
    mock.shutdown
    mat.shutdown
    Await.ready(system.terminate, 10 seconds)
  }

  def e1 = {
    val beacon = network.resolveLocalXPlaneBeacon
    mock.broadcast(100 millis)

    beacon must beEqualTo(mock.becn).await
  }

  def e2 = {
    val done = XPlaneApi.connect flatMap { api =>
      after(100 millis, system.scheduler)(api.disconnect)
    }

    mock.broadcast(100 millis)

    done must beEqualTo(Done).await
  }

  def e3 = withConnectedApi(api => Future.successful(api.beacon)) must beEqualTo(mock.becn).await

  def e4 = {
    val dataRefs = withConnectedApi { api =>
      api.subscribeToDataRefs(100, "/mock/dataref").toMat(Sink.head)(Keep.right).run()
    }

    after(500 millis, system.scheduler)(Future(mock.send(RREF(Map(1 -> 1.0f)))))

    dataRefs must beEqualTo(Map("/mock/dataref" -> 1.0f)).await
  }

  private def withConnectedApi[T](op: ConnectedXPlaneApi => Future[T]): Future[T] =
    returning(XPlaneApi.connect flatMap { api =>
      op(api) andThen { case _ =>
        after(100 millis, system.scheduler)(api.disconnect)
      }
    }) { _ => mock.broadcast(100 millis) }
}
