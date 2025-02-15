package org.tessellation.infrastructure.snapshot

import java.security.KeyPair

import cats.effect.kernel.Async
import cats.effect.std.Random
import cats.syntax.option._

import org.tessellation.config.types.SnapshotConfig
import org.tessellation.dag.block.BlockValidator
import org.tessellation.dag.block.processing.BlockAcceptanceManager
import org.tessellation.domain.rewards.Rewards
import org.tessellation.domain.snapshot.GlobalSnapshotStorage
import org.tessellation.kryo.KryoSerializer
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.peer.PeerId
import org.tessellation.sdk.config.types.HealthCheckConfig
import org.tessellation.sdk.domain.cluster.services.Session
import org.tessellation.sdk.domain.cluster.storage.ClusterStorage
import org.tessellation.sdk.domain.gossip.Gossip
import org.tessellation.sdk.domain.node.NodeStorage
import org.tessellation.sdk.infrastructure.consensus.Consensus
import org.tessellation.sdk.infrastructure.metrics.Metrics
import org.tessellation.security.SecurityProvider

import org.http4s.client.Client

object GlobalSnapshotConsensus {

  def make[F[_]: Async: Random: KryoSerializer: SecurityProvider: Metrics](
    gossip: Gossip[F],
    selfId: PeerId,
    keyPair: KeyPair,
    seedlist: Option[Set[PeerId]],
    collateral: Amount,
    clusterStorage: ClusterStorage[F],
    nodeStorage: NodeStorage[F],
    globalSnapshotStorage: GlobalSnapshotStorage[F],
    blockValidator: BlockValidator[F],
    healthCheckConfig: HealthCheckConfig,
    snapshotConfig: SnapshotConfig,
    client: Client[F],
    session: Session[F],
    rewards: Rewards[F]
  ): F[Consensus[F, GlobalSnapshotEvent, GlobalSnapshotKey, GlobalSnapshotArtifact]] =
    Consensus.make[F, GlobalSnapshotEvent, GlobalSnapshotKey, GlobalSnapshotArtifact](
      GlobalSnapshotConsensusFunctions.make[F](
        globalSnapshotStorage,
        BlockAcceptanceManager.make[F](blockValidator),
        collateral,
        rewards
      ),
      gossip,
      selfId,
      keyPair,
      snapshotConfig.timeTriggerInterval,
      seedlist,
      clusterStorage,
      nodeStorage,
      healthCheckConfig,
      client,
      session,
      none
    )

}
