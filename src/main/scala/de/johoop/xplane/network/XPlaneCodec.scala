package de.johoop.xplane.network

import java.nio.ByteBuffer

import de.johoop.xplane.network.Message.DecodingError

trait XPlaneEncoder[T] {
  def encode(t: T): ByteBuffer
}

trait XPlaneDecoder[T] {
  def decode(bytes: ByteBuffer): Either[DecodingError, T]
}

