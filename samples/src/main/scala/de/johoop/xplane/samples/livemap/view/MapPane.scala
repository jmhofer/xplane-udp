package de.johoop.xplane.samples.livemap.view

import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.control.Button
import scalafx.scene.layout.{BorderPane, Priority, VBox}

class MapPane(liveMapView: LiveMapView) extends BorderPane {
  val connect = new Button { text = "Connect" }

  hgrow = Priority.Always
  vgrow = Priority.Always

  center = liveMapView.map

  right = new VBox {
    padding = Insets(20)

    children = Seq(
      liveMapView.zoom,
      liveMapView.mapType,
      connect
    )
  }
}
