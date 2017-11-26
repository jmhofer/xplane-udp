package de.johoop.xplane

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import de.johoop.xplane.api.XPlaneApi

import scala.concurrent.duration._
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
    implicit val timeout: Timeout = Timeout(1 second)
    import system.dispatcher

    XPlaneApi.connect() flatMap { api =>
      println("Beacon: " + api.beacon)

      val source = api.subscribeToDataRefs(1,
        "sim/flightmodel/weight/m_fixed",
        "sim/flightmodel/weight/m_total",
        "sim/flightmodel/weight/m_fuel[0]",
        "sim/flightmodel/weight/m_fuel[1]",
        "sim/aircraft/overflow/acf_num_tanks",
        "sim/aircraft/weight/acf_m_fuel_tot")

      val done = source
        .take(15)
        .idleTimeout(20 seconds)
        .toMat(Sink.foreach { dataRefs =>
          println("DataRefs:")
          println(dataRefs.toSeq.mkString("\n"))
          println()
        })(Keep.right)
        .run()

      // TODO do something with the source, change some dataref, check

      done recover { case e =>
          println(s"failed: ${e.getMessage}")
          Done
      } andThen { case _ => api.disconnect }
    }

// example for modifying the fuel tank load of the plane
// setDataRef("sim/flightmodel/weight/m_fuel[0]", 153.0f)
// setDataRef("sim/flightmodel/weight/m_fuel[1]", 0.0f)

  }
}
