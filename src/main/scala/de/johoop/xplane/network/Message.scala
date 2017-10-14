package de.johoop.xplane.network

import java.nio.ByteBuffer

abstract class Message

object Message {
  case class DecodingError(msg: String) extends AnyVal

  implicit class EnrichedMessage[T](msg: T) {
    def encode(implicit enc: XPlaneEncoder[T]): ByteBuffer = enc.encode(msg)
  }

  implicit class EnrichedByteBuffer(b: ByteBuffer) {
    def decode[T](implicit dec: XPlaneDecoder[T]): Either[DecodingError, T] = dec.decode(b)
  }
}
