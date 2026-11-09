package org.totschnig.myexpenses.db2

import android.content.ContentValues
import android.content.Context
import java.math.BigDecimal
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_UUID
import org.totschnig.myexpenses.provider.PORTFOLIO_ASSET
import org.totschnig.myexpenses.provider.PORTFOLIO_CASH
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.data.CategoryRef
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import org.totschnig.myexpenses.viewmodel.data.TradeType
import org.totschnig.myexpenses.viewmodel.data.TransactionEditData
import org.totschnig.myexpenses.viewmodel.data.TransferEditData
import org.totschnig.myexpenses.viewmodel.data.mapper.TransactionMapper.mapTransaction

/**
 * Writes trades of a portfolio: buys, sells, cash movements and transfers of assets between
 * portfolios. A trade is a split in the portfolio, whose parts move money and quantity to the
 * sub accounts, one per asset and at most one for cash. Missing sub accounts are created.
 * Runs on the calling thread.
 */
class TradeSaver(private val repository: Repository, private val localizedContext: Context) {
    private val prefHandler get() = repository.prefHandler
    private val currencyContext get() = repository.currencyContext
    private val contentResolver get() = repository.contentResolver

    private var lastFeeCategories: List<CategoryRef>? = null

    /**
     * The fee categories of the last saved trade, as remembered across sessions.
     */
    fun loadLastFeeCategories(): List<CategoryRef> =
        prefHandler.getString(PrefKey.LAST_TRADE_FEE_CATEGORIES)
            ?.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.mapNotNull { catId ->
                repository.getCategoryPath(catId)?.let { path -> CategoryRef(catId, path) }
            }
            ?: emptyList()

    /**
     * Saves [intents] in [currentAccount] in one bulk operation and returns the fee categories
     * of the last saved trade, or null when no intent stored fee categories (transfers do not).
     */
    fun saveTrades(
        currentAccount: FullAccount,
        intents: List<TradeIntent>,
        onProgress: (Int) -> Unit = {}
    ): List<CategoryRef>? {
        lastFeeCategories = null
        repository.bulkStart()
        try {
            val knownSubaccounts = repository.loadSubAccounts(currentAccount.id).map {
                FullAccount(
                    id = it.id,
                    label = it.label,
                    currencyUnit = currencyContext[it.currency],
                    type = it.type!!,
                    portfolioRole = it.portfolioRole,
                    parentId = it.parentId
                )
            }.toMutableList()
            intents.forEachIndexed { index, intent ->
                saveTradeInternal(currentAccount, intent, knownSubaccounts)
                onProgress(index + 1)
            }
        } finally {
            repository.bulkEnd()
        }
        return lastFeeCategories
    }

    private fun saveTradeInternal(
        currentAccount: FullAccount,
        intent: TradeIntent,
        knownSubaccounts: MutableList<FullAccount>
    ) {
        if (intent.type is TradeType.Transfer) {
            saveAssetTransfer(currentAccount, intent, knownSubaccounts)
            return
        }
        val isAssetTrade = intent.type is TradeType.AssetTrade
        val portfolioCurrency = currentAccount.currencyUnit
        val transferCategory = prefHandler.defaultTransferCategory

        val targetAccountId = if (isAssetTrade) {
            findOrCreateAssetAccount(
                currentAccount.id,
                intent.targetAsset,
                knownSubaccounts
            )
        } else {
            findOrCreateCashAccount(
                currentAccount.id,
                currentAccount.currency,
                currentAccount.color,
                knownSubaccounts
            )
        }

        val principalAmount = intent.principal
        val totalImpact = if (intent.type.isIncoming) {
            principalAmount + intent.fee
        } else {
            principalAmount - intent.fee
        }

        val parts = mutableListOf<TransactionEditData>()

        // Part A: Target Leg (Portfolio <-> Asset/Cash sub-account)
        val targetLegHubAmount = if (intent.type.isIncoming) {
            -principalAmount
        } else {
            principalAmount
        }
        val targetLegSubAmount = if (intent.type.isIncoming) {
            intent.quantity
        } else {
            -intent.quantity
        }

        parts.add(
            TransactionEditData(
                accountId = currentAccount.id,
                amount = targetLegHubAmount,
                transferEditData = TransferEditData(
                    transferAccountId = targetAccountId,
                    transferAmount = targetLegSubAmount
                ),
                isSplitPart = true,
                uuid = generateUuid(),
                categoryId = transferCategory
            )
        )

        // Part B: Funding/Source Leg (Portfolio <-> Cash Sub-account or External Account)
        val fundingLegHubAmount = if (intent.type.isIncoming) {
            totalImpact
        } else {
            -totalImpact
        }

        val fundingTransferAccountId =
            if (intent.linkedTransactionId != null) {
                null
            } else {
                when (intent.fundingSource) {
                    FundingSource.ACCOUNT -> intent.peerAccountId

                    FundingSource.PORTFOLIO -> findOrCreateCashAccount(
                        currentAccount.id,
                        currentAccount.currency,
                        currentAccount.color,
                        knownSubaccounts
                    )

                    FundingSource.EXTERNAL -> null
                }
            }

        val fundingLegUuid = generateUuid()
        parts.add(
            TransactionEditData(
                accountId = currentAccount.id,
                amount = fundingLegHubAmount,
                transferEditData = fundingTransferAccountId?.let {
                    TransferEditData(
                        transferAccountId = it
                    )
                },
                isSplitPart = true,
                uuid = fundingLegUuid,
                categoryId = if (intent.linkedTransactionId != null) null else transferCategory
            )
        )

        // Part C: Additional Costs / Fees / Taxes (Expenses)
        val validCosts = intent.additionalCosts.filter { it.amount.amountMinor != 0L }
        for ((amount, category) in validCosts) {
            parts.add(
                TransactionEditData(
                    accountId = currentAccount.id,
                    amount = -amount,
                    isSplitPart = true,
                    uuid = generateUuid(),
                    categoryId = category?.id
                )
            )
        }

        val categoryIdsToSave = validCosts.mapNotNull { it.category?.id }
        prefHandler.putString(
            PrefKey.LAST_TRADE_FEE_CATEGORIES,
            categoryIdsToSave.joinToString(",")
        )
        lastFeeCategories = validCosts.mapNotNull { it.category }

        // Parent transaction amount is the sum of all parts in Portfolio currency
        val totalPortfolioAmount = parts
            .fold(Money(portfolioCurrency, 0L)) { acc, part -> acc + part.amount }

        val parent = TransactionEditData(
            id = intent.tradeId ?: 0L,
            accountId = currentAccount.id,
            amount = totalPortfolioAmount,
            date = intent.date,
            comment = intent.comment,
            uuid = generateUuid(),
            categoryId = SPLIT_CATID,
            splitParts = parts
        )

        if (intent.tradeId != null) {
            repository.updateTransaction(mapTransaction(parent))
        } else {
            repository.createTransaction(mapTransaction(parent))
        }

        if (intent.linkedTransactionId != null) {
            val existingUuid = repository.getUuidForTransaction(intent.linkedTransactionId)
            if (existingUuid != null) {
                contentResolver.update(
                    TransactionProvider.TRANSACTIONS_URI.buildUpon()
                        .appendPath(TransactionProvider.URI_SEGMENT_LINK_TRANSFER)
                        .appendPath(fundingLegUuid)
                        .build(),
                    ContentValues(1).apply {
                        put(KEY_UUID, existingUuid)
                    },
                    null,
                    null
                )
            }
        }

        // Also update the Price History table for valuation
        if (intent.price > BigDecimal.ZERO) {
            repository.savePrice(
                portfolioCurrency,
                intent.targetAsset,
                intent.date.toLocalDate(),
                ExchangeRateSource.User,
                intent.price
            )
        }
    }
    private fun findOrCreateAssetAccount(
        portfolioId: Long,
        asset: CurrencyUnit,
        knownSubaccounts: MutableList<FullAccount>
    ): Long = knownSubaccounts.find { it.currency == asset.code && it.parentId == portfolioId }?.id
        ?: run {
            val account = Account(
                label = asset.description,
                currency = asset.code,
                parentId = portfolioId,
                type = repository.findAccountType(AccountType.INVESTMENT.name)!!,
                portfolioRole = PORTFOLIO_ASSET,
                dynamicExchangeRates = true
            )
            val newAccount = repository.createAccount(account)
            val fullAccount = FullAccount(
                id = newAccount.id,
                label = newAccount.label,
                currencyUnit = currencyContext[newAccount.currency],
                type = AccountType.INVESTMENT,
                portfolioRole = PORTFOLIO_ASSET,
                parentId = portfolioId
            )
            knownSubaccounts.add(fullAccount)
            newAccount.id
        }

    private fun findOrCreateCashAccount(
        portfolioId: Long,
        portfolioCurrency: String,
        portfolioColor: Int,
        knownSubaccounts: MutableList<FullAccount>
    ): Long = knownSubaccounts.find { it.type.isCashAccount && it.parentId == portfolioId }?.id
        ?: run {
            val cashAccount = Account(
                label = localizedContext.getString(R.string.account_type_cash),
                currency = portfolioCurrency,
                parentId = portfolioId,
                type = repository.findAccountType(AccountType.CASH.name)!!,
                color = portfolioColor,
                portfolioRole = PORTFOLIO_CASH
            )
            val newAccount = repository.createAccount(cashAccount)
            val fullAccount = FullAccount(
                id = newAccount.id,
                label = newAccount.label,
                currencyUnit = currencyContext[newAccount.currency],
                type = AccountType.CASH,
                portfolioRole = PORTFOLIO_CASH,
                parentId = portfolioId
            )
            knownSubaccounts.add(fullAccount)
            newAccount.id
        }

    private fun saveAssetTransfer(
        currentPortfolio: FullAccount,
        intent: TradeIntent,
        knownSubaccounts: MutableList<FullAccount>
    ) {
        val otherPortfolioId = intent.peerAccountId ?: return
        val otherPortfolio = repository.loadAccount(otherPortfolioId) ?: return

        val isIncoming = intent.type.isIncoming
        val (sourcePortfolio, targetPortfolio) = if (isIncoming) {
            otherPortfolio to currentPortfolio
        } else {
            currentPortfolio to otherPortfolio
        }

        val sourceCurrency = currencyContext[sourcePortfolio.currency]
        val targetCurrency = currencyContext[targetPortfolio.currency]
        val valuation = intent.principal
        val quantity = intent.quantity
        val transferCategory = prefHandler.defaultTransferCategory
        val dateEpoch = intent.date.toEpoch()

        val peerKnownSubAccounts = repository.loadSubAccounts(otherPortfolioId).map {
            FullAccount(
                id = it.id,
                label = it.label,
                currencyUnit = currencyContext[it.currency],
                type = it.type!!,
                portfolioRole = it.portfolioRole,
                parentId = it.parentId
            )
        }
        val sourceKnownSubaccounts = if (isIncoming) {
            peerKnownSubAccounts.toMutableList()
        } else {
            knownSubaccounts
        }

        val targetKnownSubaccounts = if (isIncoming) {
            knownSubaccounts
        } else {
            peerKnownSubAccounts.toMutableList()
        }

        val sourceAssetAccountId = findOrCreateAssetAccount(
            sourcePortfolio.id,
            intent.targetAsset,
            sourceKnownSubaccounts
        )

        val targetAssetAccountId = findOrCreateAssetAccount(
            targetPortfolio.id,
            intent.targetAsset,
            targetKnownSubaccounts
        )

        val oldParent = intent.tradeId?.let { repository.loadTransaction(it) }
        val oldSiblingParent = intent.tradeId?.let { repository.findSiblingParentId(it) }?.let {
            repository.loadTransaction(it)
        }

        val oldRepoTrans1 = intent.tradeId?.let { if (isIncoming) oldSiblingParent else oldParent }
        val oldRepoTrans2 = intent.tradeId?.let { if (isIncoming) oldParent else oldSiblingParent }

        val hubToHubUuid = oldRepoTrans1?.splitParts
            ?.find { it.data.transferAccountId == targetPortfolio.id }?.data?.uuid
            ?: generateUuid()

        // --- Source Portfolio Split ---
        val sourceParts = mutableListOf<Pair<Transaction, Transaction?>>()
        val oldParts1 = oldRepoTrans1?.splitParts ?: emptyList()

        // Leg A1: Internal Spoke (Asset -> Hub)
        val oldA1 = oldParts1.find { it.data.transferAccountId == sourceAssetAccountId }
        val legA1Source = Transaction(
            id = oldA1?.id ?: 0L,
            accountId = sourcePortfolio.id,
            amount = Money.convertBigDecimal(valuation.amountMajor, sourceCurrency.fractionDigits),
            transferAccountId = sourceAssetAccountId,
            uuid = oldA1?.data?.uuid ?: generateUuid(),
            categoryId = transferCategory,
            date = dateEpoch,
            valueDate = dateEpoch,
            transferPeerId = oldA1?.transferPeer?.id
        )
        val legA1Peer = Transaction(
            id = oldA1?.transferPeer?.id ?: 0L,
            accountId = sourceAssetAccountId,
            amount = Money.convertBigDecimal(
                quantity.amountMajor.negate(),
                intent.targetAsset.fractionDigits
            ),
            transferAccountId = sourcePortfolio.id,
            categoryId = transferCategory,
            date = dateEpoch,
            valueDate = dateEpoch,
            uuid = legA1Source.uuid,
            currency = intent.targetAsset.code,
            transferPeerId = legA1Source.id
        )
        sourceParts.add(legA1Source to legA1Peer)

        // Leg A2: External Link (Source Hub -> Target Hub)
        val oldA2 = oldParts1.find { it.data.transferAccountId == targetPortfolio.id }
        sourceParts.add(
            Transaction(
                id = oldA2?.id ?: 0L,
                accountId = sourcePortfolio.id,
                amount = Money.convertBigDecimal(
                    valuation.amountMajor.negate(),
                    sourceCurrency.fractionDigits
                ),
                transferAccountId = targetPortfolio.id,
                uuid = hubToHubUuid,
                categoryId = transferCategory,
                date = dateEpoch,
                valueDate = dateEpoch,
                transferPeerId = oldA2?.transferPeer?.id
            ) to null
        )

        val parentA = Transaction(
            id = oldRepoTrans1?.id ?: 0L,
            accountId = sourcePortfolio.id,
            amount = 0L,
            date = dateEpoch,
            valueDate = dateEpoch,
            comment = intent.comment,
            uuid = oldRepoTrans1?.data?.uuid ?: generateUuid(),
            categoryId = SPLIT_CATID,
            currency = sourceCurrency.code
        )

        // --- Target Portfolio Split ---
        val targetParts = mutableListOf<Pair<Transaction, Transaction?>>()
        val oldParts2 = oldRepoTrans2?.splitParts ?: emptyList()

        // Leg B1: External Link (Source Hub -> Target Hub)
        val oldB1 = oldParts2.find { it.data.transferAccountId == sourcePortfolio.id }
        targetParts.add(
            Transaction(
                id = oldB1?.id ?: 0L,
                accountId = targetPortfolio.id,
                amount = Money.convertBigDecimal(
                    valuation.amountMajor,
                    targetCurrency.fractionDigits
                ),
                transferAccountId = sourcePortfolio.id,
                uuid = hubToHubUuid,
                categoryId = transferCategory,
                date = dateEpoch,
                valueDate = dateEpoch,
                transferPeerId = oldB1?.transferPeer?.id
            ) to null
        )

        // Leg B2: Internal Spoke (Hub -> Asset)
        val oldB2 = oldParts2.find { it.data.transferAccountId == targetAssetAccountId }
        val legB2Source = Transaction(
            id = oldB2?.id ?: 0L,
            accountId = targetPortfolio.id,
            amount = Money.convertBigDecimal(
                valuation.amountMajor.negate(),
                targetCurrency.fractionDigits
            ),
            transferAccountId = targetAssetAccountId,
            uuid = oldB2?.data?.uuid ?: generateUuid(),
            categoryId = transferCategory,
            date = dateEpoch,
            valueDate = dateEpoch,
            transferPeerId = oldB2?.transferPeer?.id
        )
        val legB2Peer = Transaction(
            id = oldB2?.transferPeer?.id ?: 0L,
            accountId = targetAssetAccountId,
            amount = Money.convertBigDecimal(
                quantity.amountMajor,
                intent.targetAsset.fractionDigits
            ),
            transferAccountId = targetPortfolio.id,
            categoryId = transferCategory,
            date = dateEpoch,
            valueDate = dateEpoch,
            uuid = legB2Source.uuid,
            currency = intent.targetAsset.code,
            transferPeerId = legB2Source.id
        )
        targetParts.add(legB2Source to legB2Peer)

        val parentB = Transaction(
            id = oldRepoTrans2?.id ?: 0L,
            accountId = targetPortfolio.id,
            amount = 0L,
            date = dateEpoch,
            valueDate = dateEpoch,
            comment = intent.comment,
            uuid = oldRepoTrans2?.data?.uuid ?: generateUuid(),
            categoryId = SPLIT_CATID,
            currency = targetCurrency.code
        )

        if (intent.tradeId != null) {
            repository.updateDualSplitTransaction(
                RepositoryTransaction(
                    parentA,
                    splitParts = sourceParts.map { RepositoryTransaction(it.first, it.second) }
                ),
                RepositoryTransaction(
                    parentB,
                    splitParts = targetParts.map { RepositoryTransaction(it.first, it.second) }
                )
            )
        } else {
            repository.createDualSplitTransaction(parentA, sourceParts, parentB, targetParts)
        }
        if (intent.price > BigDecimal.ZERO) {
            repository.savePrice(
                currentPortfolio.currencyUnit,
                intent.targetAsset,
                intent.date.toLocalDate(),
                ExchangeRateSource.User,
                intent.price
            )
        }
    }
}
