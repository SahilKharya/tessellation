package org.tessellation.dag.snapshot

import cats.Order
import cats.kernel._
import cats.syntax.semigroup._

import org.tessellation.schema.{nonNegLongDecoder, nonNegLongEncoder}

import derevo.cats.{order, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.NonNegLong

@derive(encoder, decoder, order, show)
case class SnapshotOrdinal(value: NonNegLong)

object SnapshotOrdinal {
  val MinValue: SnapshotOrdinal = SnapshotOrdinal(NonNegLong.MinValue)

  implicit val next: Next[SnapshotOrdinal] = new Next[SnapshotOrdinal] {
    def next(a: SnapshotOrdinal): SnapshotOrdinal = SnapshotOrdinal(a.value |+| NonNegLong(1L))
    def partialOrder: PartialOrder[SnapshotOrdinal] = Order[SnapshotOrdinal]
  }

  implicit val partialPrevious: PartialPrevious[SnapshotOrdinal] = new PartialPrevious[SnapshotOrdinal] {
    def partialOrder: PartialOrder[SnapshotOrdinal] = Order[SnapshotOrdinal]

    def partialPrevious(a: SnapshotOrdinal): Option[SnapshotOrdinal] =
      refineV[NonNegative].apply[Long](a.value.value |+| -1).toOption.map(r => SnapshotOrdinal(r))
  }
}
