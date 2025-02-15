package org.tessellation.infrastructure.snapshot

import cats.effect.Async
import cats.effect.std.Queue
import cats.syntax.all._

import org.tessellation.dag.domain.block.DAGBlock
import org.tessellation.domain.aci.StateChannelOutput
import org.tessellation.sdk.domain.Daemon
import org.tessellation.sdk.domain.gossip.Gossip
import org.tessellation.sdk.infrastructure.consensus.message.ConsensusEvent
import org.tessellation.security.signature.Signed

import fs2._

object GlobalSnapshotEventsPublisherDaemon {

  def make[F[_]: Async](
    stateChannelOutputs: Queue[F, StateChannelOutput],
    l1OutputQueue: Queue[F, Signed[DAGBlock]],
    gossip: Gossip[F]
  ): Daemon[F] = Daemon.spawn {
    Stream
      .fromQueueUnterminated(stateChannelOutputs)
      .map(_.asLeft[DAGEvent])
      .merge(
        Stream
          .fromQueueUnterminated(l1OutputQueue)
          .map(_.asRight[StateChannelEvent])
      )
      .map(ConsensusEvent(_))
      .evalMap(gossip.spread[ConsensusEvent[GlobalSnapshotEvent]])
      .compile
      .drain
  }

}
