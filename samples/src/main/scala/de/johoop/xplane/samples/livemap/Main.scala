package de.johoop.xplane.samples.livemap

import java.nio.file.Files

import com.sothawo.mapjfx.offline.OfflineCache
import com.sothawo.mapjfx.{Coordinate, MapType, MapView}
import de.johoop.xplane.util.returning

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.layout._
import scalafx.scene.text.Text

object Main extends JFXApp {
  lazy val mapView: MapView = returning(new MapView) { mapView =>
    mapView.hgrow = Priority.Always
    mapView.vgrow = Priority.Always
  }

  lazy val zoomText = new Text {
    text = "Zoom Level: " + mapView.getZoom.toString
  }

  lazy val mapTypeText = new Text {
    text = "Map Type: " + mapView.getMapType.name
  }

  mapView.zoomProperty.onChange {
    zoomText.text = "Zoom Level: " + mapView.getZoom.toString
  }

  mapView.mapTypeProperty.onChange {
    mapTypeText.text = "Map Type: " + mapView.getMapType.name
  }

  lazy val mapPane = new BorderPane {
    hgrow = Priority.Always
    vgrow = Priority.Always

    center = mapView

    right = new VBox {
      padding = Insets(20)

      children = Seq(
        zoomText,
        mapTypeText
      )
    }
  }

  stage = new JFXApp.PrimaryStage {
    title.value = "X-Plane Live Map"
    width = 1024
    height = 768

    scene = new Scene {
      root = mapPane
    }
  }

  stage.onShown = { _ =>
    initializeMapView(mapView)
  }

  def initializeMapView(mapView: MapView): Unit = {
    initializeOfflineCache(mapView.getOfflineCache)

    mapView.initialize

    mapView.initializedProperty().onChange {
      mapView.setMapType(MapType.OSM)
      mapView.setZoom(4.0)

      val coordKarlsruheCastle = new Coordinate(49.013517, 8.404435)
      mapView setCenter coordKarlsruheCastle

      ()
    }
  }

  def initializeOfflineCache(cache: OfflineCache): Unit = {
    val temp = Files createTempDirectory "xplane-live-map"
    temp.toFile.deleteOnExit()
    cache setCacheDirectory temp
    cache setActive true
  }
}
