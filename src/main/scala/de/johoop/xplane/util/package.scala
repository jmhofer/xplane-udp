package de.johoop.xplane

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

package object util {
  def returning[T](t: T)(op: T => Unit): T = { op(t); t }

  def ascii(b: ByteBuffer, str: String): Unit = {
    b.put(str.getBytes(StandardCharsets.US_ASCII))
    b.put(0.toByte)
  }
  def ascii(b: ByteBuffer, str: String, length: Int): Unit = {
    val pos = b.position()
    ascii(b, str)
    b.position(pos + length)
  }

  def string(b: ByteBuffer, maxLength: Int): String = {
    val bStr = ByteBuffer allocate maxLength
    b.get(bStr.array)
    new String(bStr.array.takeWhile(_ != 0), StandardCharsets.US_ASCII)
  }
}
