package org.tessellation.dag.snapshot

import org.tessellation.dag.domain.block.DAGBlock
import org.tessellation.ext.derevo.ordering
import org.tessellation.security.signature.Signed

import derevo.cats.{order, show}
import derevo.derive
import eu.timepit.refined.cats._
import eu.timepit.refined.types.numeric.NonNegLong

@derive(order, ordering, show)
case class BlockAsActiveTip(
  block: Signed[DAGBlock],
  usageCount: NonNegLong
)
