package de.johoop.xplane.samples.livemap.view

import java.nio.file.Files

import com.sothawo.mapjfx.offline.OfflineCache
import com.sothawo.mapjfx._
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.util.returning
import de.johoop.xplane.samples.livemap.util._

import scalafx.Includes._
import scalafx.scene.layout.Priority
import scalafx.scene.text.Text

class LiveMapView {
  lazy val aircraftMarkers = 0 until 360 map { deg =>
    new Marker(getClass.getResource(s"/rotated-$deg.png"), -16, -16).setVisible(false)
  }

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
    updateMarker(rpos)

    map.setCenter(rpos.coords)
    line foreach map.addCoordinateLine
  }

  private def updateMarker(rpos: RPOS): Unit = {
    val heading = headingInDegrees(rpos)
    val currentMarker = aircraftMarkers(heading)

    val markerHadNoPositionYet = Option(currentMarker.getPosition).isEmpty
    currentMarker.setPosition(rpos.coords).setVisible(true)
    if (markerHadNoPositionYet) map.addMarker(currentMarker)

    0 until 360 filter (_ != heading) map aircraftMarkers foreach (_ setVisible false)
  }

  private def headingInDegrees(rpos: RPOS): Int = {
    val headingDeg = math.round(rpos.trueHeading)

    (if (headingDeg < 0) headingDeg + 360 else if (headingDeg >= 359) headingDeg - 360 else headingDeg).toInt
  }

  private def initializeOfflineCache(cache: OfflineCache): Unit = {
    val temp = Files createTempDirectory "xplane-live-map"
    temp.toFile.deleteOnExit()
    cache setCacheDirectory temp
    cache setActive true
  }
}
