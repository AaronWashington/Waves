package com.wavesplatform.events

import cats.syntax.monoid._
import com.wavesplatform.account.Address
import com.wavesplatform.block.{Block, MicroBlock}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.state.DiffToStateApplier.PortfolioUpdates
import com.wavesplatform.state.diffs.BlockDiffer.DetailedDiff
import com.wavesplatform.state.reader.CompositeBlockchain
import com.wavesplatform.state.{AccountDataInfo, AssetDescription, Blockchain, Diff, DiffToStateApplier}
import com.wavesplatform.transaction.{Asset, Transaction}
import monix.reactive.Observer

import scala.collection.mutable.ArrayBuffer

class BlockchainUpdateTriggersImpl(private val events: Observer[BlockchainUpdated]) extends BlockchainUpdateTriggers {

  override def onProcessBlock(block: Block, diff: DetailedDiff, blockchainBefore: Blockchain): Unit = {
    val (blockStateUpdate, txsStateUpdates) = containerStateUpdate(blockchainBefore, diff, block.transactionData)
    events.onNext(BlockAppended(block.signature, blockchainBefore.height + 1, block, blockStateUpdate, txsStateUpdates))
  }

  override def onProcessMicroBlock(microBlock: MicroBlock, diff: DetailedDiff, blockchainBefore: Blockchain): Unit = {
    val (microBlockStateUpdate, txsStateUpdates) = containerStateUpdate(blockchainBefore, diff, microBlock.transactionData)
    events.onNext(MicroBlockAppended(microBlock.totalResBlockSig, blockchainBefore.height, microBlock, microBlockStateUpdate, txsStateUpdates))
  }

  override def onRollback(toBlockId: ByteStr, toHeight: Int): Unit = events.onNext(RollbackCompleted(toBlockId, toHeight))

  override def onMicroBlockRollback(toTotalResBlockSig: ByteStr, height: Int): Unit =
    events.onNext(MicroBlockRollbackCompleted(toTotalResBlockSig, height))

  private def atomicStateUpdate(blockchainBefore: Blockchain, diff: Diff, byTransaction: Option[Transaction]): StateUpdate = {
    val blockchainAfter = CompositeBlockchain(blockchainBefore, Some(diff))

    val PortfolioUpdates(updatedBalances, updatedLeases) = DiffToStateApplier.portfolios(blockchainBefore, diff)

    val balances = ArrayBuffer.empty[(Address, Asset, Long)]
    for ((address, assetMap) <- updatedBalances; (asset, balance) <- assetMap) balances += ((address, asset, balance))

    val dataEntries = diff.accountData.toSeq.flatMap {
      case (address, AccountDataInfo(data)) =>
        data.toSeq.map { case (_, entry) => (address, entry) }
    }

    val assets: Seq[AssetStateUpdate] = for {
      a <- (diff.issuedAssets.keySet ++ diff.updatedAssets.keySet ++ diff.assetScripts.keySet ++ diff.sponsorship.keySet).toSeq
      AssetDescription(
        _,
        _,
        name,
        description,
        decimals,
        reissuable,
        totalVolume,
        _,
        script,
        sponsorship,
        nft
      ) <- blockchainAfter.assetDescription(a).toSeq
      existedBefore = !diff.issuedAssets.isDefinedAt(a)
    } yield AssetStateUpdate(
      a,
      decimals,
      name.toByteArray,
      description.toByteArray,
      reissuable,
      totalVolume,
      script,
      if (sponsorship == 0) None else Some(sponsorship),
      nft,
      existedBefore
    )

    StateUpdate(balances, updatedLeases.toSeq, dataEntries, assets)
  }

  private def containerStateUpdate(
      blockchainBefore: Blockchain,
      diff: DetailedDiff,
      transactions: Seq[Transaction]
  ): (StateUpdate, Seq[StateUpdate]) = {
    val DetailedDiff(parentDiff, txsDiffs) = diff
    val parentStateUpdate                  = atomicStateUpdate(blockchainBefore, parentDiff, None)

    val (txsStateUpdates, _) = txsDiffs
      .zip(transactions)
      .foldLeft((ArrayBuffer.empty[StateUpdate], parentDiff)) {
        case ((updates, accDiff), (txDiff, tx)) =>
          (
            updates += atomicStateUpdate(CompositeBlockchain(blockchainBefore, Some(accDiff)), txDiff, Some(tx)),
            accDiff.combine(txDiff)
          )
      }

    (parentStateUpdate, txsStateUpdates)
  }
}
