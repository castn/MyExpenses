package org.totschnig.myexpenses.next.data.portfolio

/**
 * The kinds offered in the form. A trade that is changed keeps its kind of trade: a buy can become
 * a sell, a deposit a withdrawal, a transfer stays one. A transfer is offered as [TradeKind.TRANSFER_OUT],
 * its direction is changed with [invertTransfer].
 */
fun kindChoices(original: TradeKind?): List<TradeKind> = when {
    original == null -> listOf(
        TradeKind.BUY,
        TradeKind.SELL,
        TradeKind.DEPOSIT,
        TradeKind.WITHDRAW,
        TradeKind.TRANSFER_OUT
    )

    original.isTransfer -> listOf(original)

    original.isCashMovement -> listOf(TradeKind.DEPOSIT, TradeKind.WITHDRAW)

    else -> listOf(TradeKind.BUY, TradeKind.SELL)
}

/** Whether [choice] of [kindChoices] is the kind of this draft; a transfer in either direction */
fun TradeDraft.isKind(choice: TradeKind) =
    if (choice.isTransfer) kind.isTransfer else kind == choice

/**
 * The draft with [kind]. A transfer keeps its direction. Changing to or from a transfer forgets the
 * other account, as it is a portfolio for a transfer and a funding account otherwise. Cash cannot
 * be moved from or to the cash of the portfolio itself, so that funding becomes external.
 */
fun TradeDraft.withKind(kind: TradeKind): TradeDraft {
    if (kind.isTransfer && this.kind.isTransfer) return this
    val switchesTransfer = kind.isTransfer != this.kind.isTransfer
    val funding = when {
        switchesTransfer -> Funding.EXTERNAL
        kind.isCashMovement && funding == Funding.PORTFOLIO -> Funding.EXTERNAL
        else -> funding
    }
    return copy(
        kind = kind,
        funding = funding,
        peerAccountId = if (switchesTransfer || funding != this.funding) null else peerAccountId,
        linkedTransactionId = if (switchesTransfer) null else linkedTransactionId
    )
}

/** A transfer in the other direction */
fun TradeDraft.invertTransfer() = when (kind) {
    TradeKind.TRANSFER_IN -> copy(kind = TradeKind.TRANSFER_OUT)
    TradeKind.TRANSFER_OUT -> copy(kind = TradeKind.TRANSFER_IN)
    else -> this
}

/** The draft funded from [funding], [accountId] for [Funding.ACCOUNT]; a linked transaction is forgotten */
fun TradeDraft.withFunding(funding: Funding, accountId: Long?) = copy(
    funding = funding,
    peerAccountId = accountId.takeIf { funding == Funding.ACCOUNT },
    linkedTransactionId = null
)

/**
 * The draft with asset [code]. Its latest price in [portfolio] is filled in, unless it is the
 * asset of the trade changed, whose price stays as saved.
 */
fun TradeDraft.withAsset(
    code: String,
    portfolio: Portfolio,
    original: Trade?,
    decimalSeparator: Char
): TradeDraft {
    val keepsPrice = original != null && original.asset.code == code
    val price = portfolio.holdings.find { it.asset.code == code }?.price?.takeIf { !keepsPrice }
    val text = price?.let { formatDecimalText(it, decimalSeparator) } ?: priceText
    return copy(assetCode = code, priceText = text)
}

/** The draft with one more cost, without amount and category */
fun TradeDraft.withNewCost() = copy(costs = costs + CostDraft("", null))

/** The draft with cost [index] replaced by [cost] */
fun TradeDraft.withCost(index: Int, cost: CostDraft) =
    copy(costs = costs.mapIndexed { i, old -> if (i == index) cost else old })

/** The draft without cost [index] */
fun TradeDraft.withoutCost(index: Int) = copy(costs = costs.filterIndexed { i, _ -> i != index })
