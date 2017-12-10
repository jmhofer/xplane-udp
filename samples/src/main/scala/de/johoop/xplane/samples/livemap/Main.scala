package de.johoop.xplane.samples.livemap

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.johoop.xplane.samples.livemap.view.{LiveMapView, MapPane}

import scalafx.application.JFXApp
import scalafx.scene.Scene

object Main extends JFXApp {
  implicit val system = ActorSystem("live-map")
  implicit val mat = ActorMaterializer()

  // initialize views
  lazy val liveMapView = new LiveMapView
  lazy val mapPane = new MapPane(liveMapView)

  // initialize controller
  val controller = new Controller(mapPane, liveMapView)

  // set the main stage
  stage = new JFXApp.PrimaryStage {
    title.value = "X-Plane Live Map"
    width = 1024
    height = 768

    scene = new Scene {
      root = mapPane
    }
  }

  controller.wire

  // main stage wiring
  stage.onShown = { _ =>
    liveMapView.initialize
  }

  stage.onCloseRequest = { _ =>
    mat.shutdown()
    system.terminate()
  }
}
