package de.johoop.xplane

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import de.johoop.xplane.api.XPlane

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future
import scala.util.{Failure, Success}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("xplane-main")
    implicit val mat = ActorMaterializer()

    doStuff .andThen { case _ =>
      mat.shutdown
      system.terminate
    } (Implicits.global) .onComplete {
      case Success(_) => println("Success!")
      case Failure(e) => println(s"Failure: $e")
    } (Implicits.global)
  }

  def doStuff(implicit system: ActorSystem, mat: Materializer): Future[Done] = {
    import system.dispatcher

    /*
    val plan = for {
      source <- XPlane.subscribeToDataRefs(1,
        "sim/flightmodel/weight/m_fixed",
        "sim/flightmodel/weight/m_total",
        "sim/flightmodel/weight/m_fuel[0]",
        "sim/flightmodel/weight/m_fuel[1]",
        "sim/aircraft/overflow/acf_num_tanks",
        "sim/aircraft/weight/acf_m_fuel_tot")
      _ = Thread sleep 6000L // TODO yikes, how to do this properly?
      _ <- XPlane.setDataRef("sim/flightmodel/weight/m_fuel[0]", 153.0f)
      _ <- XPlane.setDataRef("sim/flightmodel/weight/m_fuel[1]", 0.0f)
    } yield source

    Source
      .fromFuture(XPlane.run(plan))
      .flatMapConcat(identity)
      .take(15)
      .runForeach(println)

      val send = sendTo(client)
      send(RPOSRequest(1))
      send(ALRTRequest(Vector("hello", "one", "two", "three")))
    */
    Future.successful(Done)
  }
}
