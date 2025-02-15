package org.tessellation.sdk.cli

import scala.concurrent.duration._

import org.tessellation.cli.env._
import org.tessellation.schema.balance.{Amount, _}
import org.tessellation.schema.node.NodeState
import org.tessellation.sdk.config.AppEnvironment
import org.tessellation.sdk.config.AppEnvironment.Mainnet
import org.tessellation.sdk.config.types._

import eu.timepit.refined.auto.autoRefineV
import eu.timepit.refined.types.numeric.NonNegLong
import fs2.io.file.Path

trait CliMethod {

  val keyStore: StorePath
  val alias: KeyAlias
  val password: Password

  val environment: AppEnvironment

  val seedlistPath: Option[Path]

  val httpConfig: HttpConfig

  val stateAfterJoining: NodeState

  val collateralAmount: Option[Amount]

  val collateralConfig = (environment: AppEnvironment, amount: Option[Amount]) =>
    CollateralConfig(
      amount = amount
        .filter(_ => environment != Mainnet)
        .getOrElse(Amount(NonNegLong.unsafeFrom(250_000L * normalizationFactor)))
    )

  val gossipConfig: GossipConfig = GossipConfig(
    storage = RumorStorageConfig(
      activeRetention = 4.seconds,
      seenRetention = 2.minutes
    ),
    daemon = GossipDaemonConfig(
      fanout = 1,
      interval = 0.2.seconds,
      maxConcurrentRounds = 4,
      roundTimeout = 10.seconds
    )
  )

  val leavingDelay = 30.seconds

  val healthCheckConfig = HealthCheckConfig(
    removeUnresponsiveParallelPeersAfter = 10.seconds,
    requestProposalsAfter = 8.seconds,
    ping = PingHealthCheckConfig(
      concurrentChecks = 3,
      defaultCheckTimeout = 6.seconds,
      defaultCheckAttempts = 3,
      ensureCheckInterval = 10.seconds
    ),
    peerDeclaration = PeerDeclarationHealthCheckConfig(
      receiveTimeout = 20.seconds,
      triggerInterval = 10.seconds
    )
  )

  lazy val sdkConfig: SdkConfig = SdkConfig(
    environment,
    gossipConfig,
    httpConfig,
    leavingDelay,
    stateAfterJoining,
    healthCheckConfig
  )

}
