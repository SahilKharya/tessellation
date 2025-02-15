package org.tessellation.http.p2p.clients

import cats.effect.Async

import org.tessellation.dag.snapshot.{GlobalSnapshot, SnapshotOrdinal}
import org.tessellation.kryo.KryoSerializer
import org.tessellation.sdk.domain.cluster.services.Session
import org.tessellation.sdk.http.p2p.PeerResponse
import org.tessellation.sdk.http.p2p.PeerResponse.PeerResponse
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.Signed

import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

trait GlobalSnapshotClient[F[_]] {
  def getLatest: PeerResponse[F, Signed[GlobalSnapshot]]
  def getLatestOrdinal: PeerResponse[F, SnapshotOrdinal]
}

object GlobalSnapshotClient {

  def make[F[_]: Async: SecurityProvider: KryoSerializer](
    client: Client[F],
    session: Session[F]
  ): GlobalSnapshotClient[F] =
    new GlobalSnapshotClient[F] with Http4sClientDsl[F] {

      def getLatest: PeerResponse[F, Signed[GlobalSnapshot]] = {
        import org.tessellation.ext.codecs.BinaryCodec.decoder
        PeerResponse[F, Signed[GlobalSnapshot]]("global-snapshots/latest")(client, session)
      }

      def getLatestOrdinal: PeerResponse[F, SnapshotOrdinal] = {
        import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
        PeerResponse[F, SnapshotOrdinal]("global-snapshots/latest/ordinal")(client, session)
      }
    }
}
