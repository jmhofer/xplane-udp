package de.johoop.xplane

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.pattern.{after, ask}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import de.johoop.xplane.XPlaneServerMock._
import de.johoop.xplane.api.{ConnectedToXPlane, XPlane}
import de.johoop.xplane.network.protocol.Message.ProtocolError
import de.johoop.xplane.network.protocol._
import de.johoop.xplane.util.returning
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, Matcher}
import org.specs2.specification.{AfterAll, BeforeEach}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class XPlaneSpec(implicit ee: ExecutionEnv) extends Specification with BeforeEach with AfterAll with FutureMatchers { def is =
  "XPlane".title ^ sequential ^ s2"""

  The XPlane client should work.

  The low-level network package must find a broadcasting X-Plane installation and return its beacon data.  $e1

  For the high-level X-Plane API,
    connecting must succeed and produce the proper beacon,                               $e2
    connecting and retrieving the beacon must return the correct beacon information,     $e3
    subscribing to a dataref must provide us with a source of values for this dataref,   $e4
    retrieving a single dataref value must return one value,                             $e5
    subscribing to the plane position must provide us with a source of position data.    $e6
  """

  implicit val system: ActorSystem = ActorSystem("xplane-spec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(1 second)

  import system.dispatcher

  val multicastGroupName = "239.255.1.2"
  val multicastGroup = InetAddress getByName multicastGroupName

  val multicastPort = 49707

  val mock = system.actorOf(XPlaneServerMock.props(multicastGroup, multicastPort), "mock")

  val rpos = RPOS(
    longitude = -1.0, latitude = -0.5,
    elevationSeaLevel = 99.0, elevationTerrain = 10.0f,
    pitch = 0.1f, trueHeading = 0.2f, roll = -0.1f,
    speedX = 100.0f, speedY = 50.0f, speedZ = 0.0f,
    rollRate = -0.5f, pitchRate = 0.3f, yawRate = -100.0f)

  def before: Unit = mock ! ResetReceived

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
    val connected = XPlane.connect(multicastGroupName)

    mock ? Broadcast(100 millis)

    connected must haveBeacon(XPlaneServerMock.becn).await
  }

  def e3 = withConnectedApi(api => Future.successful(api.beacon)) must beEqualTo(XPlaneServerMock.becn).await

  def e4 = {
    val dataRefPath = "/mock/dataref"
    val dataRefs = withConnectedApi { api =>
      api.subscribeToDataRefs(50, dataRefPath).toMat(Sink.head)(Keep.right).run()
    }

    after(500 millis, system.scheduler)(mock ? SendRREF(RREF(Map(1 -> 1.0f))))

    val received = after(700 millis, system.scheduler)(mock.ask(GetReceived).mapTo[Vector[Either[ProtocolError, Request]]])

    received must beEqualTo(Vector(
        Right(RREFRequest(50, 1, dataRefPath)),
        Right(RREFRequest(0, 1, dataRefPath)))).awaitFor(10 seconds) and
      (dataRefs must beEqualTo(Map(dataRefPath -> 1.0f)).await(retries = 0, timeout = 10 seconds))
  }

  def e5 = {
    val dataRefPath = "/mock/singleValue"
    val value = withConnectedApi { _.getDataRef(dataRefPath) }

    after(500 millis, system.scheduler)(mock ? SendRREF(RREF(Map(1 -> -9.5f))))

    val received = after(700 millis, system.scheduler)(mock.ask(GetReceived).mapTo[Vector[Either[ProtocolError, Request]]])

    received must beEqualTo(Vector(
        Right(RREFRequest(100, 1, dataRefPath)),
        Right(RREFRequest(0, 1, dataRefPath)))).awaitFor(10 seconds) and
      (value must beEqualTo(-9.5f).awaitFor(10 seconds))
  }

  def e6 = {
    val firstPosition = withConnectedApi { api =>
      api.subscribeToRPOS(20).toMat(Sink.head)(Keep.right).run()
    }

    after(500 millis, system.scheduler)(mock ? SendRPOS(rpos))

    val received = after(700 millis, system.scheduler)(mock.ask(GetReceived).mapTo[Vector[Either[ProtocolError, Request]]])

    received must beEqualTo(Vector(Right(RPOSRequest(20), RPOSRequest(0)))).awaitFor(10 seconds) and
      (firstPosition must beEqualTo(rpos).awaitFor(10 seconds))
  }

  private def withConnectedApi[T](op: ConnectedToXPlane => Future[T]): Future[T] =
    returning(XPlane.connect(multicastGroupName) flatMap op) { _ => mock ? Broadcast(100 millis) }

  private def haveBeacon(beacon: BECN): Matcher[ConnectedToXPlane] = beEqualTo(beacon) ^^ { (_: ConnectedToXPlane).beacon }
}
