package de.johoop.xplane.network.protocol

import java.nio.ByteBuffer

abstract class Message

object Message {
  case class ProtocolError(msg: String) extends Exception

  implicit class EnrichedMessage[T](msg: T) {
    def encode(implicit enc: XPlaneEncoder[T]): ByteBuffer = enc.encode(msg)
  }

  implicit class EnrichedByteBuffer(b: ByteBuffer) {
    def decode[T](implicit dec: XPlaneDecoder[T]): Either[ProtocolError, T] = dec.decode(b)
  }
}
