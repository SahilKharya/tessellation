package org.tessellation.dag.snapshot

import org.tessellation.dag.domain.block.BlockReference
import org.tessellation.ext.derevo.ordering

import derevo.cats.{order, show}
import derevo.derive
import eu.timepit.refined.cats._
import eu.timepit.refined.types.numeric.NonNegLong

@derive(order, ordering, show)
case class ActiveTip(
  block: BlockReference,
  usageCount: NonNegLong,
  introducedAt: SnapshotOrdinal
)
