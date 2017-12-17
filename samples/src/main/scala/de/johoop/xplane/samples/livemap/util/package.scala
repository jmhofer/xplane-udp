package de.johoop.xplane.samples.livemap

import com.sothawo.mapjfx.Coordinate
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.util.math.Vector3d

package object util {
  implicit class EnrichedRPOS(rpos: RPOS) {
    def coords: Coordinate = new Coordinate(rpos.latitude, rpos.longitude)
  }

  implicit class EnrichedCoordinate(coord: Coordinate) {
    def toVector3d: Vector3d = Vector3d(coord.getLatitude, coord.getLongitude, 0.0)
  }

  implicit class EnrichedVector3d(v3d: Vector3d) {
    def toCoordinate: Coordinate = new Coordinate(v3d.x, v3d.y)
  }
}
