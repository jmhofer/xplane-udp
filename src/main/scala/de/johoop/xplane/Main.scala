package de.johoop.xplane

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import de.johoop.xplane.api.XPlane

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
    import system.dispatcher

    XPlane.connect() flatMap { api =>
      println("Beacon: " + api.beacon)

      // number of fuel tanks: "sim/aircraft/overflow/acf_num_tanks"
      // total fuel capacity: "sim/aircraft/weight/acf_m_fuel_tot"
      // total weight: "sim/flightmodel/weight/m_total",

      api.getDataRef("sim/aircraft/weight/acf_m_fuel_tot") foreach { totalFuelCapacity =>
        println("Total fuel capacity: " + totalFuelCapacity)
      }

      api.getDataRef("sim/flightmodel/weight/m_fixed") foreach { fixedWeight =>
        println("Fixed weight: " + fixedWeight)
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
        _ <- doneLeftFuel
        _ <- doneRightFuel
      } yield Done

      done recover { case e =>
        println(s"failed: ${e.getMessage}")
        Done
      }
    }
  }
}
