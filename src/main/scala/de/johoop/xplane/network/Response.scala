package de.johoop.xplane.network

import java.nio.{ByteBuffer, ByteOrder}

import de.johoop.xplane.network.Message._
import de.johoop.xplane.util.{returning, string}
import scala.annotation.tailrec

case class Response(opcode: String, payload: Payload) extends Message

sealed abstract class Payload

case class BECN(
  majorVersion: Int,
  minorVersion: Int,
  hostId: Int,
  versionNumber: Int,
  role: Int,
  port: Int,
  computerName: String) extends Payload

case class RPOS(
  longitude: Double,
  latitude: Double,
  elevationSeaLevel: Double,
  elevationTerrain: Float,
  pitch: Float,
  trueHeading: Float,
  roll: Float,
  speedX: Float,
  speedY: Float,
  speedZ: Float,
  rollRate: Float,
  pitchRate: Float,
  yawRate: Float) extends Payload

case class RREF(dataRefs: Int Map Float) extends Payload

object Response {
  implicit object BECNDecoder extends XPlaneDecoder[BECN] {
    def decode(b: ByteBuffer): Either[DecodingError, BECN] = Right(BECN(
      majorVersion = b.get,
      minorVersion = b.get,
      hostId = b.getInt,
      versionNumber = b.getInt,
      role = b.getInt,
      port = java.lang.Short.toUnsignedInt(b.getShort),
      computerName = string(b, 500)
    ))
  }

  implicit object RPOSDecoder extends XPlaneDecoder[RPOS] {
    def decode(b: ByteBuffer): Either[DecodingError, RPOS] = Right(RPOS(
      longitude = b.getDouble,
      latitude = b.getDouble,
      elevationSeaLevel = b.getDouble,
      elevationTerrain = b.getFloat,
      pitch = b.getFloat,
      trueHeading = b.getFloat,
      roll = b.getFloat,
      speedX = b.getFloat,
      speedY = b.getFloat,
      speedZ = b.getFloat,
      rollRate = b.getFloat,
      pitchRate = b.getFloat,
      yawRate = b.getFloat
    ))
  }

  implicit object RREFDecoder extends XPlaneDecoder[RREF] {
    override def decode(b: ByteBuffer): Either[DecodingError, RREF] = {
      @tailrec
      def loop(acc: Int Map Float = Map.empty): Int Map Float = {
        val id = b.getInt
        if (id == 0) acc else loop(acc.updated(id, b.getFloat))
      }

      Right(RREF(loop()))
    }
  }

  implicit object ResponseDecoder extends XPlaneDecoder[Payload] {
    def decode(b: ByteBuffer): Either[DecodingError, Payload] = {
      b.rewind
      b.order(ByteOrder.LITTLE_ENDIAN)
      returning(string(b, 4))(_ => b.get) match {
        case "BECN" => b.decode[BECN]
        case "RPOS" => b.decode[RPOS]
        case "RREF" => b.decode[RREF]

        case other => Left(DecodingError(s"unknown opcode: $other"))
      }
    }
  }
}