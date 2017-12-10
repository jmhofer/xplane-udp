package de.johoop.xplane.samples.livemap

import com.sothawo.mapjfx.Coordinate
import de.johoop.xplane.network.protocol.RPOS

package object util {
  implicit class EnrichedRPOS(rpos: RPOS) {
    def coords: Coordinate = new Coordinate(rpos.latitude, rpos.longitude)
  }
}
