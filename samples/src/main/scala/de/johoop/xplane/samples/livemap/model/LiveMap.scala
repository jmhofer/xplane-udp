package de.johoop.xplane.samples.livemap.model

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.KillSwitch
import akka.stream.scaladsl.Source
import de.johoop.xplane.api.XPlane
import de.johoop.xplane.network.protocol.RPOS

case class LiveMap(killSwitch: Option[KillSwitch])

object LiveMap {
  def initialize(implicit system: ActorSystem): Source[RPOS, NotUsed] = {
    import system.dispatcher

    Source.fromFuture(XPlane.connect()) flatMapConcat { connected =>
      connected.subscribeToRPOS(1)
    }
  }
}
