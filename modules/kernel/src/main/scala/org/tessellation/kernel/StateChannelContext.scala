package org.tessellation.kernel

import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Balance

trait StateChannelContext[F[_]] {
  val address: Address

  def createCell(
    input: Array[Byte],
    hypergraphContext: HypergraphContext[F]
  ): F[Cell[F, StackF, Ω, Either[CellError, Ω], Ω]]

  def getBalance(address: Address): F[Balance]

  def setBalance(address: Address, balance: Balance): F[Unit]

  // TODO: @mwadon - probably transfer(from: Address, to: Address, balance: Balance) needed

}
