package de.johoop.xplane

import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.{after, ask}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import de.johoop.xplane.XPlaneServerMock.{Broadcast, GetReceived, SendRREF, ShutDown}
import de.johoop.xplane.api.{ConnectedXPlaneApi, XPlaneApi}
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol.{RREF, RREFRequest, Request}
import de.johoop.xplane.util.returning
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.AfterAll

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class XPlaneSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers { def is =
  "XPlane".title ^ sequential ^ s2"""

  The XPlane client should work.

  The low-level network package must find a broadcasting X-Plane installation and return its beacon data.  $e1

  For the high-level X-Plane API,
    connecting and immediately disconnecting again must succeed,                         $e2
    connecting and retrieving the beacon must return the correct beacon information,     $e3
    subscribing to a dataref must provide us with a source of values for this dataref,   $e4
    retrieving a single dataref value must return one value.                             $e5
  """

  implicit val system: ActorSystem = ActorSystem("xplane-spec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(1 second)

  import system.dispatcher

  val multicastGroupName = "239.255.1.2"
  val multicastGroup = InetAddress getByName multicastGroupName

  val multicastPort = 49707

  val mock = system.actorOf(XPlaneServerMock.props(multicastGroup, multicastPort), "mock")

  def afterAll: Unit = {
    mock ! ShutDown
    mat.shutdown
    Await.ready(system.terminate, 10 seconds)
  }

  def e1 = {
    val beacon = network.resolveLocalXPlaneBeacon(multicastGroup, multicastPort)
    mock ? Broadcast(100 millis)

    beacon must beEqualTo(XPlaneServerMock.becn).await
  }

  def e2 = {
    val done = XPlaneApi.connect(multicastGroupName) flatMap { api =>
      after(100 millis, system.scheduler)(api.disconnect)
    }

    mock ? Broadcast(100 millis)

    done must beEqualTo(Done).await
  }

  def e3 = withConnectedApi(api => Future.successful(api.beacon)) must beEqualTo(XPlaneServerMock.becn).await

  def e4 = {
    val dataRefPath = "/mock/dataref"
    val dataRefs = withConnectedApi { api =>
      api.subscribeToDataRefs(100, dataRefPath).toMat(Sink.head)(Keep.right).run()
    }

    after(500 millis, system.scheduler)(mock ? SendRREF(RREF(Map(1 -> 1.0f))))

    val received = after(700 millis, system.scheduler)(mock.ask(GetReceived).mapTo[Vector[Either[ProtocolError, Request]]])

    received must beEqualTo(Vector(
        Right(RREFRequest(100, 1, dataRefPath)),
        Right(RREFRequest(0, 1, dataRefPath)))).awaitFor(10 seconds) and
      (dataRefs must beEqualTo(Map(dataRefPath -> 1.0f)).await(retries = 0, timeout = 10 seconds))
  }

  def e5 = {
    val dataRefPath = "/mock/singleValue"
    val value = withConnectedApi { _.getDataRef(dataRefPath) }

    after(500 millis, system.scheduler)(mock ? SendRREF(RREF(Map(1 -> -9.5f))))

    value must beEqualTo(-9.5f).awaitFor(10 seconds)
  }

  private def withConnectedApi[T](op: ConnectedXPlaneApi => Future[T]): Future[T] =
    returning(XPlaneApi.connect(multicastGroupName) flatMap { api =>
      op(api) andThen { case _ =>
        after(100 millis, system.scheduler)(api.disconnect)
      }
    }) { _ => mock ? Broadcast(100 millis) }
}
