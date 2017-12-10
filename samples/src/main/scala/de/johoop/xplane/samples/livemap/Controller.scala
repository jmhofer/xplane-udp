package de.johoop.xplane.samples.livemap

import akka.actor.ActorSystem
import akka.stream.{KillSwitches, Materializer}
import akka.stream.scaladsl.{Keep, Sink}
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.samples.livemap.model.LiveMap
import de.johoop.xplane.samples.livemap.view.{LiveMapView, MapPane}

import scalafx.Includes._
import scalafx.application.Platform

class Controller(mapPane: MapPane, liveMapView: LiveMapView)(implicit system: ActorSystem, mat: Materializer) {
  private var liveMap = LiveMap(killSwitch = None)

  def wire: Unit = {
    liveMapView.map.zoomProperty.onChange {
      liveMapView.zoom.text = "Zoom Level: " + liveMapView.map.getZoom.toString
    }
    liveMapView.map.mapTypeProperty.onChange {
      liveMapView.mapType.text = "Map Type: " + liveMapView.map.getMapType.name
    }

    mapPane.connect.onAction = { _ =>
      liveMap.killSwitch match {
        case None =>
          mapPane.connect.text = "Disconnect"

          liveMap = liveMap.copy(killSwitch = Some(LiveMap.initialize
            .viaMat(KillSwitches.single)(Keep.right)
            .scan(Option.empty[RPOS]) { (previous, current) =>
              Platform.runLater(liveMapView.update(previous, current))
              Option(current)
            }
            .to(Sink.ignore)
            .run()
          ))

        case Some(killSwitch) =>
          mapPane.connect.text = "Connect"
          killSwitch.shutdown()
          liveMap = liveMap.copy(killSwitch = None)
      }
    }
  }
}
