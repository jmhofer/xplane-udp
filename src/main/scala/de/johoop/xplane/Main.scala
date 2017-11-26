package de.johoop.xplane

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.after
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
    } (Implicits.global) .onComplete { // TODO make sure that this actually manages to stop the application
      case Success(_) => println("Success!")
      case Failure(e) => println(s"Failure: $e")
    } (Implicits.global)
  }

  def doStuff(implicit system: ActorSystem, mat: Materializer): Future[Done] = {
    implicit val timeout: Timeout = Timeout(1 second)
    import system.dispatcher

    XPlaneApi.connect() flatMap { api =>
      println("Beacon: " + api.beacon)

      // fixed weight: "sim/flightmodel/weight/m_fixed"
      // number of fuel tanks: "sim/aircraft/overflow/acf_num_tanks"
      // total fuel capacity: "sim/aircraft/weight/acf_m_fuel_tot"
      // total weight: "sim/flightmodel/weight/m_total",

      api.getDataRef("sim/aircraft/weight/acf_m_fuel_tot") foreach { totalFuelCapacity =>
        // FIXME somehow, unsubscribing seems to get into conflict with the other received values
        // TODO check what might be going on here, and that it's not a bug on the X-Plane side
        println("Total fuel capacity: " + totalFuelCapacity)
      }

      val sourceForLeftFuel = api.subscribeToDataRefs(1, "sim/flightmodel/weight/m_fuel[0]")

      val doneLeftFuel = sourceForLeftFuel
        .take(10)
        .idleTimeout(12 seconds)
        .toMat(Sink.foreach { dataRefs =>
          println("Left Fuel:")
          println(dataRefs.toSeq.mkString("\n"))
          println()
        })(Keep.right)
        .run()

      val sourceForRightFuel = api.subscribeToDataRefs(1, "sim/flightmodel/weight/m_fuel[1]")

      val doneRightFuel = sourceForRightFuel
        .take(10)
        .idleTimeout(12 seconds)
        .toMat(Sink.foreach { dataRefs =>
          println("Right Fuel:")
          println(dataRefs.toSeq.mkString("\n"))
          println()
        })(Keep.right)
        .run()

      after(5 seconds, using = system.scheduler) {
        api.setDataRef("sim/flightmodel/weight/m_fuel[1]", 0.0f)
      }

      val done = for {
        left <- doneLeftFuel
        right <- doneRightFuel
      } yield Done

      done recover { case e =>
          println(s"failed: ${e.getMessage}")
          Done
      } andThen { case _ => api.disconnect }
    }
  }
}
