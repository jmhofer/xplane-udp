package de.johoop.xplane.samples.livemap.tool

import java.awt.image.BufferedImage
import java.nio.file.Paths
import javax.imageio.ImageIO

/** Creates the rotated versions of the aircraft icon for use as map markers. */
object ImageRotator {
  val src = ImageIO.read(getClass.getResource("/aircraft-small.png"))

  0 until 360 foreach { deg =>
    val rotated = new BufferedImage(src.getWidth, src.getHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = rotated.createGraphics

    graphics.rotate(deg / 180.0 * math.Pi, 16, 16)
    graphics.drawImage(src, 0, 0, null)

    ImageIO.write(rotated, "png",
      Paths.get(s"C:/daten/prog/scala/xplane/samples/src/main/resources/rotated-$deg.png").toFile)
    graphics.dispose
  }
}
