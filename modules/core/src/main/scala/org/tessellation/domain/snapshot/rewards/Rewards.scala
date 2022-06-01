package org.tessellation.domain.snapshot.rewards

import cats.data.NonEmptyList

import scala.collection.immutable.SortedSet

import org.tessellation.schema.ID.Id
import org.tessellation.schema.transaction.RewardTransaction

object Rewards {

  def calculateRewards(facilitators: NonEmptyList[Id]): SortedSet[RewardTransaction] =
    SortedSet.empty

}
