package org.tessellation.dag.snapshot

import org.tessellation.dag.domain.block.BlockReference
import org.tessellation.ext.derevo.ordering

import derevo.cats.{order, show}
import derevo.derive

@derive(order, ordering, show)
case class DeprecatedTip(
  block: BlockReference,
  deprecatedAt: SnapshotOrdinal
)
