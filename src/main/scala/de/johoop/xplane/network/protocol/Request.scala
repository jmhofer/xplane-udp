package de.johoop.xplane.network.protocol

import java.nio.{ByteBuffer, ByteOrder}

import de.johoop.xplane.util.{ascii, returning}

sealed abstract class Request extends Message

case class DREFRequest(value: Float, path: String) extends Request
case class RPOSRequest(positionsPerSecond: Int) extends Request
case class RREFRequest(frequency: Int, id: Int, path: String) extends Request
case class ALRTRequest(msgs: Vector[String]) extends Request

object Request {
  implicit object RequestEncoder extends XPlaneEncoder[Request] { // TODO maybe this can be "automatic" using Shapeless
    override def encode(req: Request): ByteBuffer = req match {
      case rpos: RPOSRequest => RPOSEncoder encode rpos
      case dref: DREFRequest => DREFEncoder encode dref
      case rref: RREFRequest => RREFEncoder encode rref
      case alrt: ALRTRequest => ALRTEncoder encode alrt
    }
  }

  implicit val RPOSEncoder: XPlaneEncoder[RPOSRequest] = encodeHelper[RPOSRequest](16, "RPOS") { (msg, b) =>
    ascii(b, msg.positionsPerSecond.toString)
  }

  implicit val DREFEncoder: XPlaneEncoder[DREFRequest] = encodeHelper[DREFRequest](509, "DREF") { (msg, b) =>
    b.putFloat(msg.value)
    ascii(b, msg.path)
  }

  implicit val RREFEncoder: XPlaneEncoder[RREFRequest] = encodeHelper[RREFRequest](413, "RREF") { (msg, b) =>
    b.putInt(msg.frequency)
    b.putInt(msg.id)
    ascii(b, msg.path, 400)
  }

  implicit val ALRTEncoder: XPlaneEncoder[ALRTRequest] = encodeHelper[ALRTRequest](965, "ALRT") { (msg, b) =>
    msg.msgs take math.min(msg.msgs.length, 4) foreach { msg =>
      ascii(b, msg.substring(0, math.min(msg.length, 239)), 240)
    }
  }

  private[network] def encodeHelper[T](size: Int, opcode: String)(op: (T, ByteBuffer) => Unit): XPlaneEncoder[T] =
    (t: T) => returning(ByteBuffer allocate size) { b =>
      b.order(ByteOrder.LITTLE_ENDIAN)
      ascii(b, opcode)
      op(t, b)
      b.rewind
    }
}
