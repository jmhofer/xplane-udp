package de.johoop.xplane.samples.livemap.util

import com.sothawo.mapjfx.{Coordinate, CoordinateLine}
import de.johoop.xplane.network.protocol.RPOS
import de.johoop.xplane.util.returning

case class Track(coords: Vector[Coordinate] = Vector.empty, lines: Vector[CoordinateLine] = Vector.empty)

// TODO maybe optimise the track in order to avoid eating so much memory

object Track {
  def update(track: Track, rpos: RPOS): Track = {
    val updatedCoords = track.coords :+ rpos.coords

    val updatedLines = updatedCoords match {
      case Vector(_) => track.lines
      case lines => track.lines :+ line(lines.init.last, lines.last)
    }

    track.copy(updatedCoords, updatedLines)
  }

  private def line(previous: Coordinate, current: Coordinate): CoordinateLine =
    returning(new CoordinateLine(previous, current)) { _ setVisible true }
}
