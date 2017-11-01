package de.johoop.xplane.network.protocol

import java.nio.ByteBuffer

import de.johoop.xplane.network.protocol.Message.ProtocolError

trait XPlaneEncoder[T] {
  def encode(t: T): ByteBuffer
}

trait XPlaneDecoder[T] {
  def decode(bytes: ByteBuffer): Either[ProtocolError, T]
}

