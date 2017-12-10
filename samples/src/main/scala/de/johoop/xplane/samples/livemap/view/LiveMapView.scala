package de.johoop.xplane.samples.livemap.view

import java.nio.file.Files

import com.sothawo.mapjfx.offline.OfflineCache
import com.sothawo.mapjfx.{Coordinate, CoordinateLine, MapType, MapView}
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.util.returning
import de.johoop.xplane.samples.livemap.util._

import scalafx.Includes._
import scalafx.scene.layout.Priority
import scalafx.scene.text.Text

// TODO display plane sprite and rotate it according to the plane's direction

class LiveMapView {
  lazy val map: MapView = returning(new MapView) { mapView =>
    mapView.hgrow = Priority.Always
    mapView.vgrow = Priority.Always
  }

  lazy val zoom = new Text { text = "Zoom Level: " + map.getZoom.toString }
  lazy val mapType = new Text { text = "Map Type: " + map.getMapType.name }

  def initialize: Unit = {
    initializeOfflineCache(map.getOfflineCache)

    map.initialize

    map.initializedProperty.onChange {
      map.setMapType(MapType.OSM)
      map.setZoom(4.0)

      val coordKarlsruheCastle = new Coordinate(49.013517, 8.404435)
      map setCenter coordKarlsruheCastle

      ()
    }
  }

  def update(rpos: RPOS, line: Option[CoordinateLine]): Unit = {
    map.setCenter(rpos.coords)
    line foreach map.addCoordinateLine
  }

  private def initializeOfflineCache(cache: OfflineCache): Unit = {
    val temp = Files createTempDirectory "xplane-live-map"
    temp.toFile.deleteOnExit()
    cache setCacheDirectory temp
    cache setActive true
  }
}
