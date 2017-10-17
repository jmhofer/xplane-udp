package de.johoop.xplane.network.protocol

import java.nio.ByteBuffer

import de.johoop.xplane.network.protocol.Message.DecodingError

trait XPlaneEncoder[T] {
  def encode(t: T): ByteBuffer
}

trait XPlaneDecoder[T] {
  def decode(bytes: ByteBuffer): Either[DecodingError, T]
}

