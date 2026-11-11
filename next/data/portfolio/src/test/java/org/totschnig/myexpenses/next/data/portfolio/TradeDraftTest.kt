package org.totschnig.myexpenses.next.data.portfolio

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

class TradeDraftTest {

    private val now = LocalDateTime.of(2026, 3, 10, 9, 15)
    private val apple = Asset("AAPL", "Apple", 0, AssetKind.SECURITY)
    private val bitcoin = Asset("BTC", "Bitcoin", 8, AssetKind.CRYPTO)
    private val portfolio = Portfolio(
        id = 1,
        label = "Depot",
        currency = CurrencyInfo("EUR", "€", 2),
        isSealed = false,
        cashBalance = 50000,
        holdings = listOf(
            Holding(11, apple, 10, BigDecimal("180.5")),
            Holding(12, bitcoin, 50_000_000, null)
        )
    )
    private val fees = listOf(CostCategory(7, "Gebühren"), CostCategory(8, "Steuern"))

    private fun draft(kind: TradeKind = TradeKind.BUY) =
        newTradeDraft(portfolio, kind, now, fees, ',')

    private fun trade(kind: TradeKind = TradeKind.SELL, quantity: Long = 4) = Trade(
        id = 5,
        portfolioId = 1,
        kind = kind,
        date = now,
        asset = apple,
        quantity = quantity,
        price = BigDecimal("175.25"),
        principal = 70100,
        costs = listOf(CostLeg(495, fees[0])),
        peer = AccountChoice(3, "Giro"),
        comment = "Teilverkauf"
    )

    @Test
    fun newTradeStartsWithFirstHoldingItsPriceAndTheLastFeeCategories() {
        val draft = draft()
        assertEquals("AAPL", draft.assetCode)
        assertEquals("180,5", draft.priceText)
        assertEquals(listOf(CostDraft("", fees[0]), CostDraft("", fees[1])), draft.costs)
        assertEquals(Funding.EXTERNAL, draft.funding)
        assertTrue(draft.isNew)
    }

    @Test
    fun newCashMovementHasNoPriceAndATransferNoCosts() {
        assertEquals("", draft(TradeKind.DEPOSIT).priceText)
        assertEquals(emptyList<CostDraft>(), draft(TradeKind.TRANSFER_OUT).costs)
    }

    @Test
    fun changedTradeKeepsItsGroupOfKinds() {
        assertEquals(5, kindChoices(null).size)
        assertEquals(listOf(TradeKind.BUY, TradeKind.SELL), kindChoices(TradeKind.SELL))
        assertEquals(listOf(TradeKind.DEPOSIT, TradeKind.WITHDRAW), kindChoices(TradeKind.DEPOSIT))
        assertEquals(listOf(TradeKind.TRANSFER_IN), kindChoices(TradeKind.TRANSFER_IN))
    }

    @Test
    fun cashMovementIsNotFundedByThePortfolioItself() {
        val funded = draft().withFunding(Funding.PORTFOLIO, null)
        assertEquals(Funding.EXTERNAL, funded.withKind(TradeKind.WITHDRAW).funding)
        assertEquals(Funding.PORTFOLIO, funded.withKind(TradeKind.SELL).funding)
    }

    @Test
    fun switchingToATransferForgetsTheFundingAccount() {
        val funded = draft().withFunding(Funding.ACCOUNT, 3).copy(linkedTransactionId = 99)
        val transfer = funded.withKind(TradeKind.TRANSFER_OUT)
        assertNull(transfer.peerAccountId)
        assertNull(transfer.linkedTransactionId)
        assertEquals(3L, funded.withKind(TradeKind.SELL).peerAccountId)
    }

    @Test
    fun transferKeepsItsDirectionWhenChosenAgain() {
        val incoming = draft(TradeKind.TRANSFER_OUT).invertTransfer()
        assertEquals(TradeKind.TRANSFER_IN, incoming.kind)
        assertEquals(TradeKind.TRANSFER_IN, incoming.withKind(TradeKind.TRANSFER_OUT).kind)
        assertTrue(incoming.isKind(TradeKind.TRANSFER_OUT))
    }

    @Test
    fun choosingAnAssetFillsItsLatestPriceButNotForTheTradeChanged() {
        val other = Asset("MSFT", "Microsoft", 0, AssetKind.SECURITY)
        val withPrice = portfolio.copy(
            holdings =
                portfolio.holdings + Holding(13, other, 1, BigDecimal("400"))
        )
        assertEquals("400", draft().withAsset("MSFT", withPrice, null, ',').priceText)
        val changed = trade().toDraft(emptyList(), portfolio.currency, ',')
        assertEquals("175,25", changed.withAsset("AAPL", withPrice, trade(), ',').priceText)
        // Without a known price the typed one stays
        assertEquals("180,5", draft().withAsset("BTC", portfolio, null, ',').priceText)
    }

    @Test
    fun figuresRoundThePrincipalAndAddCostsToABuy() {
        val draft = draft().copy(
            quantityText = "3",
            priceText = "10,005",
            costs = listOf(CostDraft("4,95", null), CostDraft("", fees[1]))
        )
        val up = draft.figures(portfolio, apple, null, RoundingMode.HALF_UP)
        assertEquals(3002L, up.principal)
        assertEquals(495L, up.costs)
        assertEquals(3497L, up.total)
        assertTrue(up.isRoundingNecessary)
        assertNull(up.available)
        assertEquals(3001L, draft.figures(portfolio, apple, null, RoundingMode.DOWN).principal)
    }

    @Test
    fun sellReceivesThePrincipalWithoutCostsAndShowsWhatIsHeld() {
        val draft = draft(
            TradeKind.SELL
        ).copy(quantityText = "12", priceText = "100", costs = listOf(CostDraft("5", null)))
        val figures = draft.figures(portfolio, apple, null, RoundingMode.HALF_UP)
        assertEquals(120000L - 500L, figures.total)
        assertFalse(figures.isRoundingNecessary)
        assertEquals(Available(10, apple.quantityFormat), figures.available)
        assertTrue(figures.isOverLimit)
    }

    @Test
    fun whatTheTradeChangedSoldCountsAsAvailableAgain() {
        val changed = trade(
            quantity = 4
        ).toDraft(emptyList(), portfolio.currency, ',').copy(quantityText = "12")
        val figures = changed.figures(portfolio, apple, trade(quantity = 4), RoundingMode.HALF_UP)
        assertEquals(14L, figures.available?.amount)
        assertFalse(figures.isOverLimit)
    }

    @Test
    fun withdrawalShowsTheCashOfThePortfolio() {
        val figures = draft(TradeKind.WITHDRAW).copy(quantityText = "600")
            .figures(portfolio, null, null, RoundingMode.HALF_UP)
        assertEquals(Available(50000, portfolio.currency), figures.available)
        assertTrue(figures.isOverLimit)
    }

    @Test
    fun canSaveNeedsQuantityAssetAndTheOtherPortfolioOfATransfer() {
        assertFalse(draft().canSave)
        assertTrue(draft().copy(quantityText = "1").canSave)
        assertFalse(draft().copy(quantityText = "1", assetCode = null).canSave)
        assertTrue(draft(TradeKind.DEPOSIT).copy(quantityText = "1", assetCode = null).canSave)
        assertFalse(draft(TradeKind.TRANSFER_OUT).copy(quantityText = "1").canSave)
        assertTrue(
            draft(TradeKind.TRANSFER_OUT).copy(quantityText = "1", peerAccountId = 2).canSave
        )
    }

    @Test
    fun valuesOfABuyInMinorUnits() {
        val values = draft().copy(
            assetCode = "BTC",
            quantityText = "0,015",
            priceText = "60000",
            costs = listOf(CostDraft("1,5", fees[0]), CostDraft("", fees[1])),
            comment = " Sparplan "
        ).withFunding(Funding.ACCOUNT, 3).copy(linkedTransactionId = 42)
            .toValues(portfolio, bitcoin, RoundingMode.HALF_UP)
        assertEquals(
            TradeValues(
                kind = TradeKind.BUY,
                assetCode = "BTC",
                date = now,
                quantity = 1_500_000,
                price = BigDecimal("60000"),
                principal = 90000,
                funding = Funding.ACCOUNT,
                peerAccountId = 3,
                costs = listOf(CostLeg(150, fees[0])),
                comment = "Sparplan",
                linkedTransactionId = 42,
                tradeId = null
            ),
            values
        )
    }

    @Test
    fun valuesOfACashMovementAreInThePortfolioCurrency() {
        val values = draft(TradeKind.DEPOSIT).copy(quantityText = "250,5", costs = emptyList())
            .toValues(portfolio, null, RoundingMode.HALF_UP)!!
        assertEquals("EUR", values.assetCode)
        assertEquals(25050L, values.quantity)
        assertEquals(BigDecimal.ONE, values.price)
        assertEquals(25050L, values.principal)
    }

    @Test
    fun valuesOfATransferHaveNoCostsAndTooLargeNumbersNone() {
        val transfer = draft(TradeKind.TRANSFER_OUT).copy(
            quantityText = "2",
            peerAccountId = 2,
            costs = listOf(CostDraft("3", null))
        ).toValues(portfolio, apple, RoundingMode.HALF_UP)!!
        assertEquals(emptyList<CostLeg>(), transfer.costs)
        assertNull(
            draft().copy(
                quantityText = "99999999999999999999"
            ).toValues(portfolio, apple, RoundingMode.HALF_UP)
        )
    }

    @Test
    fun draftOfATradeShowsItsValues() {
        val draft = trade().toDraft(listOf(AccountChoice(3, "Giro")), portfolio.currency, ',')
        assertEquals("4", draft.quantityText)
        assertEquals("175,25", draft.priceText)
        assertEquals(listOf(CostDraft("4,95", fees[0])), draft.costs)
        assertEquals(Funding.ACCOUNT, draft.funding)
        assertEquals(5L, draft.toValues(portfolio, apple, RoundingMode.HALF_UP)?.tradeId)
        // A peer that is no funding account is the cash of the portfolio
        assertEquals(
            Funding.PORTFOLIO,
            trade().toDraft(emptyList(), portfolio.currency, ',').funding
        )
    }

    @Test
    fun saveAndNewKeepsKindDayAssetAndFunding() {
        val next = trade().toDraft(
            listOf(AccountChoice(3, "Giro")),
            portfolio.currency,
            ','
        ).next(fees.take(1))
        assertTrue(next.isNew)
        assertEquals(TradeKind.SELL, next.kind)
        assertEquals("AAPL", next.assetCode)
        assertEquals(3L, next.peerAccountId)
        assertEquals("", next.quantityText)
        assertEquals(listOf(CostDraft("", fees[0])), next.costs)
    }

    @Test
    fun matchingLooksForMoneyOutOfTheFundingAccountForABuy() {
        val draft = draft().copy(quantityText = "2", priceText = "100", costs = emptyList())
        val figures = draft.figures(portfolio, apple, null, RoundingMode.HALF_UP)
        assertNull(draft.matchQuery(figures))
        assertEquals(
            MatchQuery(3, -20000, LocalDate.of(2026, 3, 10)),
            draft.withFunding(Funding.ACCOUNT, 3).matchQuery(figures)
        )
    }

    @Test
    fun theOnlyMatchIsLinkedAndALinkedOneStaysWhileItMatches() {
        val match = MatchingTransaction(42, "Broker", -20000, LocalDate.of(2026, 3, 10))
        val other = match.copy(id = 43)
        assertEquals(42L, draft().linkAfter(listOf(match)).linkedTransactionId)
        assertNull(draft().linkAfter(listOf(match, other)).linkedTransactionId)
        assertEquals(
            43L,
            draft().copy(
                linkedTransactionId = 43
            ).linkAfter(listOf(match, other)).linkedTransactionId
        )
        assertNull(
            draft().copy(linkedTransactionId = 43).linkAfter(emptyList()).linkedTransactionId
        )
    }

    @Test
    fun costsAreAddedChangedAndRemoved() {
        val draft = draft().withNewCost()
        assertEquals(3, draft.costs.size)
        val changed = draft.withCost(2, CostDraft("1", fees[0]))
        assertEquals(CostDraft("1", fees[0]), changed.costs[2])
        assertEquals(
            listOf(CostDraft("", fees[1]), CostDraft("1", fees[0])),
            changed.withoutCost(0).costs
        )
    }
}
