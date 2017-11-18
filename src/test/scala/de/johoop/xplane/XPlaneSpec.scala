package de.johoop.xplane

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.johoop.xplane.api.XPlane
import de.johoop.xplane.network.protocol.Response._
import de.johoop.xplane.network.protocol.RREF
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.AfterAll

import scala.concurrent.Await
import scala.concurrent.duration._

class XPlaneSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with FutureMatchers { def is = "XPlane".title ^ sequential ^ s2"""
  The XPlane client should work.

  broadcasting $e1

  meep $e2
  """

  implicit val system: ActorSystem = ActorSystem("xplane-spec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val mock = new XPlaneServerMock()

  def afterAll: Unit = {
    mock.shutdown
    mat.shutdown
    Await.ready(system.terminate, 10 seconds)
  }

  def e1 = {
    val becn = network.resolveLocalXPlaneBeacon
    Thread sleep 100
    mock.broadcast

    becn must beEqualTo(mock.becn).await(retries = 0, timeout = 1 second)
  }

  def e2 = { // TODO this should probably be done on a lower level (mocking the XPlaneActor instead)
    val result = Source.fromFuture(XPlane.run(XPlane.subscribeToDataRefs(1, "/meep")/*XPlane.getDataRef("/meep")*/)).flatMapConcat(identity)
    result.take(1).runForeach { e => println("wheeee: " + e) }

    Thread sleep 100
    mock.broadcast
    Thread sleep 100
    mock send RREF(Map(0 -> -1.0f, 1 -> 1.0f))
    mock send RREF(Map(0 -> -1.1f, 1 -> 0.9f))

    Thread sleep 5000
//    result must beEqualTo(-1.0f).await(retries = 0, timeout = 3 seconds)
    todo
  }
}
