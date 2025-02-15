package org.tessellation.sdk.domain.cluster.programs

import cats.Applicative
import cats.effect.Async
import cats.effect.std.Queue
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.show._
import cats.syntax.traverse._

import org.tessellation.effects.GenUUID
import org.tessellation.ext.crypto._
import org.tessellation.kryo.KryoSerializer
import org.tessellation.schema.ID.Id
import org.tessellation.schema.cluster._
import org.tessellation.schema.node.NodeState
import org.tessellation.schema.peer._
import org.tessellation.sdk.config.AppEnvironment
import org.tessellation.sdk.config.AppEnvironment.Dev
import org.tessellation.sdk.domain.cluster.services.{Cluster, Session}
import org.tessellation.sdk.domain.cluster.storage.{ClusterStorage, SessionStorage}
import org.tessellation.sdk.domain.node.NodeStorage
import org.tessellation.sdk.http.p2p.clients.SignClient
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import com.comcast.ip4s.{Host, IpLiteralSyntax, Port}
import fs2.{Pipe, Stream}
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Joining {

  def make[F[_]: Async: GenUUID: SecurityProvider: KryoSerializer](
    environment: AppEnvironment,
    nodeStorage: NodeStorage[F],
    clusterStorage: ClusterStorage[F],
    signClient: SignClient[F],
    cluster: Cluster[F],
    session: Session[F],
    sessionStorage: SessionStorage[F],
    seedlist: Option[Set[PeerId]],
    selfId: PeerId,
    stateAfterJoining: NodeState,
    peerDiscovery: PeerDiscovery[F],
    versionHash: Hash
  ): F[Joining[F]] =
    Queue
      .unbounded[F, P2PContext]
      .flatMap(
        make(
          _,
          environment,
          nodeStorage,
          clusterStorage,
          signClient,
          cluster,
          session,
          sessionStorage,
          seedlist,
          selfId,
          stateAfterJoining,
          peerDiscovery,
          versionHash
        )
      )

  def make[F[_]: Async: GenUUID: SecurityProvider: KryoSerializer](
    joiningQueue: Queue[F, P2PContext],
    environment: AppEnvironment,
    nodeStorage: NodeStorage[F],
    clusterStorage: ClusterStorage[F],
    signClient: SignClient[F],
    cluster: Cluster[F],
    session: Session[F],
    sessionStorage: SessionStorage[F],
    seedlist: Option[Set[PeerId]],
    selfId: PeerId,
    stateAfterJoining: NodeState,
    peerDiscovery: PeerDiscovery[F],
    versionHash: Hash
  ): F[Joining[F]] = {

    val logger = Slf4jLogger.getLogger[F]

    val joining = new Joining(
      environment,
      nodeStorage,
      clusterStorage,
      signClient,
      cluster,
      session,
      sessionStorage,
      seedlist,
      selfId,
      stateAfterJoining,
      versionHash,
      joiningQueue
    ) {}

    def join: Pipe[F, P2PContext, Unit] =
      in =>
        in.evalMap { peer =>
          {
            joining.twoWayHandshake(peer, none) >>
              peerDiscovery
                .discoverFrom(peer)
                .handleErrorWith { err =>
                  logger.error(err)(s"Peer discovery from peer ${peer.show} failed").as(Set.empty)
                }
                .flatMap(_.toList.traverse(joiningQueue.offer(_).void))
                .void
          }.handleErrorWith { err =>
            logger.error(err)(s"Joining to peer ${peer.show} failed")
          }
        }

    val process = Stream.fromQueueUnterminated(joiningQueue).through(join).compile.drain

    Async[F].start(process).as(joining)
  }
}

sealed abstract class Joining[F[_]: Async: GenUUID: SecurityProvider: KryoSerializer] private (
  environment: AppEnvironment,
  nodeStorage: NodeStorage[F],
  clusterStorage: ClusterStorage[F],
  signClient: SignClient[F],
  cluster: Cluster[F],
  session: Session[F],
  sessionStorage: SessionStorage[F],
  seedlist: Option[Set[PeerId]],
  selfId: PeerId,
  stateAfterJoining: NodeState,
  versionHash: Hash,
  joiningQueue: Queue[F, P2PContext]
) {

  private val logger = Slf4jLogger.getLogger[F]

  def join(toPeer: PeerToJoin): F[Unit] =
    for {
      _ <- validateJoinConditions(toPeer)
      _ <- session.createSession

      _ <- joiningQueue.offer(toPeer)
    } yield ()

  def rejoin(withPeer: PeerToJoin): F[Unit] =
    validateHandshakeConditions(withPeer) >> twoWayHandshake(withPeer, None, skipJoinRequest = true).void

  private def validateJoinConditions(toPeer: PeerToJoin): F[Unit] =
    for {
      nodeState <- nodeStorage.getNodeState

      canJoinCluster <- nodeStorage.canJoinCluster
      _ <-
        if (!canJoinCluster) NodeStateDoesNotAllowForJoining(nodeState).raiseError[F, Unit]
        else Applicative[F].unit

      _ <- validateHandshakeConditions(toPeer)
    } yield ()

  private def validateHandshakeConditions(toPeer: PeerToJoin): F[Unit] =
    validateHandshakeConditions(toPeer.id, toPeer.ip, toPeer.p2pPort)

  def joinRequest(hasCollateral: PeerId => F[Boolean])(joinRequest: JoinRequest, remoteAddress: Host): F[Unit] = {
    for {
      _ <- sessionStorage.getToken.flatMap(_.fold(SessionDoesNotExist.raiseError[F, Unit])(_ => Applicative[F].unit))

      registrationRequest = joinRequest.registrationRequest

      _ <- validateHandshakeConditions(joinRequest.registrationRequest)

      _ <- hasCollateral(registrationRequest.id).flatMap(CollateralNotSatisfied.raiseError[F, Unit].unlessA)

      withPeer = PeerToJoin(
        registrationRequest.id,
        registrationRequest.ip,
        registrationRequest.p2pPort
      )
      _ <- twoWayHandshake(withPeer, remoteAddress.some, skipJoinRequest = true)
    } yield ()
  }.onError(err => logger.error(err)(s"Error during join attempt by ${joinRequest.registrationRequest.id.show}"))

  private def validateHandshakeConditions(req: RegistrationRequest): F[Unit] =
    validateHandshakeConditions(req.id, req.ip, req.p2pPort)

  private def validateHandshakeConditions(id: PeerId, ip: Host, p2pPort: Port): F[Unit] =
    clusterStorage.hasPeerId(id).ifM(PeerIdInUse(id).raiseError[F, Unit], Applicative[F].unit).flatMap { _ =>
      clusterStorage
        .hasPeerHostPort(ip, p2pPort)
        .ifM(PeerHostPortInUse(ip, p2pPort).raiseError[F, Unit], Applicative[F].unit)
    }

  private def twoWayHandshake(
    withPeer: PeerToJoin,
    remoteAddress: Option[Host],
    skipJoinRequest: Boolean = false
  ): F[Peer] =
    for {
      _ <- validateSeedlist(withPeer)

      registrationRequest <- signClient.getRegistrationRequest.run(withPeer)

      _ <- validateHandshake(registrationRequest, remoteAddress)

      signRequest <- GenUUID[F].make.map(SignRequest.apply)
      signedSignRequest <- signClient.sign(signRequest).run(withPeer)

      _ <- verifySignRequest(signRequest, signedSignRequest, PeerId._Id.get(withPeer.id))
        .ifM(Applicative[F].unit, HandshakeSignatureNotValid.raiseError[F, Unit])

      _ <-
        if (skipJoinRequest) {
          Applicative[F].unit
        } else {
          clusterStorage
            .setClusterSession(registrationRequest.clusterSession)
            .flatMap(_ => cluster.getRegistrationRequest)
            .map(JoinRequest.apply)
            .flatMap(signClient.joinRequest(_).run(withPeer))
            .ifM(
              Applicative[F].unit,
              new Throwable(s"Unexpected error occured when joining with peer=${withPeer.id}.").raiseError[F, Unit]
            )
        }

      peer = Peer(
        registrationRequest.id,
        registrationRequest.ip,
        registrationRequest.publicPort,
        registrationRequest.p2pPort,
        registrationRequest.session,
        registrationRequest.state
      )

      _ <- clusterStorage.addPeer(peer)

      // Note: Changing state from SessionStarted to Ready state will execute once for first peer, then all consecutive joins should be ignored
      _ <- nodeStorage.tryModifyState(NodeState.SessionStarted, stateAfterJoining).handleError(_ => ())
    } yield peer

  private def validateSeedlist(peer: PeerToJoin): F[Unit] =
    seedlist match {
      case None => Applicative[F].unit
      case Some(entries) =>
        if (entries.contains(peer.id)) Applicative[F].unit else PeerNotInSeedlist(peer.id).raiseError[F, Unit]
    }

  private def validateHandshake(registrationRequest: RegistrationRequest, remoteAddress: Option[Host]): F[Unit] =
    for {

      _ <- VersionMismatch.raiseError[F, Unit].whenA(registrationRequest.version =!= versionHash)

      ip <- registrationRequest.ip.pure[F]

      ownClusterId = clusterStorage.getClusterId

      _ <-
        if (registrationRequest.clusterId == ownClusterId)
          Applicative[F].unit
        else ClusterIdDoesNotMatch.raiseError[F, Unit]

      ownClusterSession <- clusterStorage.getClusterSession

      _ <- ownClusterSession match {
        case Some(session) if session === registrationRequest.clusterSession => Applicative[F].unit
        case None                                                            => Applicative[F].unit
        case _                                                               => ClusterSessionDoesNotMatch.raiseError[F, Unit]
      }

      _ <-
        if (environment == Dev || ip.toString != host"127.0.0.1".toString && ip.toString != host"localhost".toString)
          Applicative[F].unit
        else LocalHostNotPermitted.raiseError[F, Unit]

      _ <- remoteAddress.fold(Applicative[F].unit)(ra =>
        if (ip.compare(ra) == 0) Applicative[F].unit else InvalidRemoteAddress.raiseError[F, Unit]
      )

      _ <- if (registrationRequest.id != selfId) Applicative[F].unit else IdDuplicationFound.raiseError[F, Unit]

      seedlistHash <- seedlist.hashF

      _ <-
        if (registrationRequest.seedlist === seedlistHash) Applicative[F].unit
        else SeedlistDoesNotMatch.raiseError[F, Unit]

    } yield ()

  private def verifySignRequest(signRequest: SignRequest, signed: Signed[SignRequest], id: Id): F[Boolean] =
    for {
      isSignedRequestConsistent <- (signRequest == signed.value).pure[F]
      isSignerCorrect = signed.proofs.forall(_.id == id)
      hasValidSignature <- signed.hasValidSignature
    } yield isSignedRequestConsistent && isSignerCorrect && hasValidSignature
}
