package de.johoop.xplane.samples.livemap.view

import java.nio.file.Files

import com.sothawo.mapjfx.offline.OfflineCache
import com.sothawo.mapjfx.{Coordinate, CoordinateLine, MapType, MapView}
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.util.returning

import scalafx.Includes._
import scalafx.scene.layout.Priority
import scalafx.scene.text.Text

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

  def update(previousRPOS: Option[RPOS], currentRPOS: RPOS): Unit = {
    map.setCenter(currentRPOS.coords)
    previousRPOS foreach { previous =>
      map.addCoordinateLine(returning(new CoordinateLine(previous.coords, currentRPOS.coords)) { _.setVisible(true) })
    }
  }

  private def initializeOfflineCache(cache: OfflineCache): Unit = {
    val temp = Files createTempDirectory "xplane-live-map"
    temp.toFile.deleteOnExit()
    cache setCacheDirectory temp
    cache setActive true
  }

  implicit class EnrichedRPOS(rpos: RPOS) {
    def coords: Coordinate = new Coordinate(rpos.latitude, rpos.longitude)
  }
}
