package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.balance.BalancePeriod
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractRule
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.tags.TagMeaning

class BudgetAnalyzerTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 20)
    private val giro = 1L
    private val savingsAccount = 3L

    private val food = CategoryInfo(10, "Essen", "Essen")
    private val leisure = CategoryInfo(20, "Freizeit", "Freizeit")
    private val gifts = CategoryInfo(30, "Geschenke", "Geschenke")
    private val categories = listOf(food, leisure, gifts).associateBy { it.id }

    private val oneOffTag = 1L
    private val reimbursedTag = 2L
    private val tripTag = 3L
    private val meanings = mapOf(oneOffTag to TagMeaning.ONE_OFF, reimbursedTag to TagMeaning.REIMBURSED, tripTag to TagMeaning.TRIP)

    private var nextId = 1000L

    private fun spend(
        amount: Long,
        date: LocalDate,
        category: CategoryInfo? = food,
        tags: Set<Long> = emptySet(),
        id: Long = nextId++,
        account: Long = giro,
        target: Long? = null,
    ) = LedgerTransaction(
        id = id, date = date, amount = -amount, accountId = account, categoryId = category?.id,
        tagIds = tags, targetAccountId = target
    )

    private fun contract(
        ruleId: String,
        amount: Long,
        vararg dates: LocalDate,
        firstId: Long,
        reserve: Boolean? = null,
        category: CategoryInfo? = leisure,
    ) = Contract(
        signature = ruleId,
        name = ruleId,
        interval = ContractInterval.MONTHLY,
        transactions = dates.mapIndexed { i, date ->
            ContractTransaction(id = firstId + i, date = date, amount = amount, accountId = giro, categoryPath = category?.path)
        },
        nextExpectedDate = dates.last().plusMonths(1),
        isActive = true,
        direction = ContractDirection.of(amount),
        isConfirmed = true,
        rule = ContractRule(
            id = ruleId, kind = ContractRule.Kind.CONTRACT, direction = ContractDirection.of(amount),
            interval = ContractInterval.MONTHLY
        ),
        reserveChoice = reserve
    )

    /** The ledger transactions of the payments of [contract] */
    private fun payments(contract: Contract, category: CategoryInfo? = leisure, target: Long? = null) =
        contract.transactions.map { spend(-it.amount, it.date, category, id = it.id, target = target) }

    private fun analyse(
        transactions: List<LedgerTransaction>,
        vararg pots: Pot,
        contracts: List<Contract> = emptyList(),
        salaries: List<Contract> = emptyList(),
        offset: Int = 0,
    ) = BudgetAnalyzer.analyse(
        BudgetInput(
            ledger = Ledger(transactions, OwnAccounts(setOf(giro), emptyMap())),
            pots = pots.toList(),
            categories = categories,
            contracts = contracts,
            salaries = salaries,
            meanings = meanings,
            today = today,
            offset = offset
        )
    )

    private fun BudgetOverview.status(pot: Pot) = pots.single { it.pot.id == pot.id }

    @Test
    fun calendarMonthsWithoutSalary() {
        val periods = budgetPeriods(null, today, LocalDate.of(2026, 7, 15))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
            periods.map { it.start }
        )
        assertEquals(BalancePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), false), periods.last())
    }

    @Test
    fun periodsFromSalaryToSalary() {
        val salary = contract("salary", 300000, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 28), firstId = 1)
        val periods = budgetPeriods(salary, today, LocalDate.of(2026, 1, 1))
        assertEquals(
            listOf(
                BalancePeriod(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 28), true),
                BalancePeriod(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 28), true)
            ),
            periods
        )
    }

    @Test
    fun runningPotCountsThePeriodWithReturnsAndWithoutReimbursedOrOneOff() {
        val pot = Pot(name = "Essen", kind = PotKind.RUNNING, amount = 40000, categoryIds = setOf(food.id))
        val status = analyse(
            listOf(
                spend(10000, LocalDate.of(2026, 10, 2)),
                spend(5000, LocalDate.of(2026, 10, 5)),
                spend(-2000, LocalDate.of(2026, 10, 6)), // returned
                spend(9000, LocalDate.of(2026, 10, 7), tags = setOf(reimbursedTag)),
                spend(60000, LocalDate.of(2026, 10, 8), tags = setOf(oneOffTag), id = 7),
                spend(8000, LocalDate.of(2026, 9, 30)), // period before
            ),
            pot
        ).status(pot)
        assertEquals(13000, status.spent)
        assertEquals(60000, status.oneOff)
        assertEquals(listOf(7L), status.oneOffIds)
        assertEquals(3, status.transactionIds.size)
        assertEquals(27000, status.left)
        assertFalse(status.isOverspent)
        // Without start, all periods before count, at most 12
        assertEquals(BudgetAnalyzer.HISTORY_PERIODS, status.history.size)
        assertEquals(8000L, status.history.last())
    }

    @Test
    fun projectsThePaceOfTheCurrentPeriod() {
        val pot = Pot(name = "Essen", kind = PotKind.RUNNING, amount = 40000, categoryIds = setOf(food.id))
        // 20 of 31 days passed
        val status = analyse(listOf(spend(20000, LocalDate.of(2026, 10, 3))), pot).status(pot)
        assertEquals(20000L * 31 / 20, status.projected)
        val earlier = analyse(listOf(spend(20000, LocalDate.of(2026, 9, 3))), pot, offset = 1)
        assertNull(earlier.status(pot).projected)
        assertEquals(LocalDate.of(2026, 9, 1), earlier.period.start)
        assertEquals(20000, earlier.status(pot).spent)
    }

    @Test
    fun warnsAboutThePotOverspentMostAtThisPace() {
        val foodPot = Pot(name = "Essen", kind = PotKind.RUNNING, amount = 30000, categoryIds = setOf(food.id))
        val going = Pot(name = "Freizeit", kind = PotKind.RUNNING, amount = 10000, categoryIds = setOf(leisure.id))
        val giftsPot = Pot(name = "Geschenke", kind = PotKind.RUNNING, amount = 1000, categoryIds = setOf(gifts.id))
        // 20 of 31 days passed: food on track for 31000, leisure for 12400, gifts already over
        val overview = analyse(
            listOf(
                spend(20000, LocalDate.of(2026, 10, 3)),
                spend(8000, LocalDate.of(2026, 10, 3), leisure),
                spend(5000, LocalDate.of(2026, 10, 3), gifts),
            ),
            foodPot, going, giftsPot
        )
        assertEquals(going, overview.paceWarning()?.pot)
        assertNull(analyse(emptyList(), foodPot).paceWarning())
    }

    @Test
    fun rolloverCarriesWhatWasLeftSinceTheStart() {
        val pot = Pot(
            name = "Ausgehen", kind = PotKind.RUNNING, amount = 10000, categoryIds = setOf(leisure.id),
            rollover = true, start = LocalDate.of(2026, 8, 15)
        )
        val status = analyse(
            listOf(
                spend(99000, LocalDate.of(2026, 7, 10), leisure), // before the start
                spend(6000, LocalDate.of(2026, 8, 10), leisure),
                spend(13000, LocalDate.of(2026, 9, 10), leisure),
                spend(2000, LocalDate.of(2026, 10, 10), leisure),
            ),
            pot
        ).status(pot)
        assertEquals(listOf(6000L, 13000L), status.history)
        assertEquals(4000 - 3000L, status.carried)
        assertEquals(10000 + 1000 - 2000L, status.left)
    }

    @Test
    fun contractOfAPotCountsBookedAndUpcoming() {
        val gym = contract("gym", -3500, LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 25), firstId = 50)
        val pot = Pot(name = "Fitness", kind = PotKind.RUNNING, amount = 5000, contractRuleIds = setOf("gym"))
        val overview = analyse(payments(gym), pot, contracts = listOf(gym))
        val status = overview.status(pot)
        assertEquals(0, status.spent)
        assertEquals(3500, status.upcoming)
        assertEquals(1500, status.left)
        // The gym is a fixed cost, only the rest of the pot is planned on top
        assertEquals(3500, overview.frame.fixed)
        assertEquals(1500, overview.frame.planned)
    }

    @Test
    fun contractsNotInAPotStayOutOfPotsWithTheirCategory() {
        val stream = contract("stream", -1300, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10), firstId = 60)
        val pot = Pot(name = "Freizeit", kind = PotKind.RUNNING, amount = 5000, categoryIds = setOf(leisure.id))
        val status = analyse(payments(stream), pot, contracts = listOf(stream)).status(pot)
        assertEquals(0, status.spent)
        assertEquals(0, status.upcoming)
    }

    @Test
    fun yearlyPotAddsAShareEachMonth() {
        val pot = Pot(
            name = "Geschenke", kind = PotKind.YEARLY, amount = 60000, categoryIds = setOf(gifts.id),
            start = LocalDate.of(2026, 8, 20)
        )
        val overview = analyse(
            listOf(
                spend(4000, LocalDate.of(2026, 8, 1), gifts), // before the start
                spend(7000, LocalDate.of(2026, 9, 5), gifts),
                spend(2000, LocalDate.of(2026, 10, 5), gifts, tags = setOf(oneOffTag)),
            ),
            pot
        )
        val status = overview.status(pot)
        // August, September, October
        assertEquals(15000, status.planned)
        assertEquals(9000, status.spent)
        assertEquals(2000, status.spentInPeriod)
        assertEquals(6000, status.left)
        assertEquals(5000, overview.frame.planned)
    }

    @Test
    fun goalCountsReservesAndMovementsToSavingAccountsOnce() {
        val bauspar = contract("bauspar", -5000, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), firstId = 70, reserve = true)
        val pot = Pot(
            name = "Neues Spiel", kind = PotKind.GOAL, amount = 20000, categoryIds = setOf(leisure.id),
            start = LocalDate.of(2026, 9, 15), reserveRuleIds = setOf("bauspar"), savingAccountIds = setOf(savingsAccount),
            end = LocalDate.of(2026, 12, 24)
        )
        val overview = analyse(
            payments(bauspar, target = savingsAccount) + listOf(
                spend(3000, LocalDate.of(2026, 10, 3), null, target = savingsAccount),
                // the other side of the movement, on the saving account
                spend(-3000, LocalDate.of(2026, 10, 3), null, account = savingsAccount, target = giro),
                spend(1000, LocalDate.of(2026, 10, 9), null, target = 9),
            ),
            pot,
            contracts = listOf(bauspar)
        )
        val status = overview.status(pot)
        assertEquals(8000, status.saved)
        assertEquals(12000, status.left)
        assertEquals(0.4f, status.progress, 0.001f)
        // Linked to a reserve, which the monthly balance holds already
        assertEquals(0, overview.frame.planned)
    }

    @Test
    fun goalWithoutLinkPlansAMonthlyRate() {
        val pot = Pot(name = "Neues Spiel", kind = PotKind.GOAL, amount = 9000, end = LocalDate.of(2026, 12, 24))
        assertEquals(3000, analyse(emptyList(), pot).frame.planned)
    }

    @Test
    fun projectTakesItsTransactionsFromEverydayPots() {
        val going = Pot(name = "Essen", kind = PotKind.RUNNING, amount = 40000, categoryIds = setOf(food.id))
        val trip = Pot(
            name = "Italien", kind = PotKind.PROJECT, amount = 150000, tagIds = setOf(tripTag),
            start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 14)
        )
        val overview = analyse(
            listOf(
                spend(5000, LocalDate.of(2026, 10, 3), tags = setOf(tripTag)),
                spend(2000, LocalDate.of(2026, 10, 4)),
            ),
            going, trip
        )
        assertEquals(5000, overview.status(trip).spent)
        assertEquals(145000, overview.status(trip).left)
        assertEquals(2000, overview.status(going).spent)
    }

    @Test
    fun frameFromIncomeFixedCostsAndPots() {
        val salary = contract("salary", 300000, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), firstId = 1)
        val rent = contract("rent", -100000, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 3), firstId = 10, category = null)
        val pot = Pot(name = "Essen", kind = PotKind.RUNNING, amount = 40000, categoryIds = setOf(food.id))
        val transactions = payments(rent, null) +
                salary.transactions.map { spend(-it.amount, it.date, null, id = it.id) } +
                listOf(
                    spend(10000, LocalDate.of(2026, 10, 5)),
                    spend(3000, LocalDate.of(2026, 10, 6), leisure),
                )
        val overview = analyse(transactions, pot, contracts = listOf(rent), salaries = listOf(salary))
        assertTrue(overview.period.isSalaryCycle)
        val frame = overview.frame
        assertEquals(300000, frame.income)
        assertEquals(100000, frame.fixed)
        assertEquals(40000, frame.planned)
        assertEquals(160000, frame.unplanned)
        assertEquals(3000, frame.spentOutsidePots)
        // 300000 - 100000 - 13000 booked, less 30000 still in the pot
        assertEquals(157000, frame.free)
    }

    @Test
    fun itemGroupPotWatchesTheShareInScannedPurchases() {
        val pot = Pot(name = "Süßes", kind = PotKind.ITEM_GROUP, itemGroups = setOf("sweets_snacks"), maxSharePercent = 10)
        fun item(amount: Long, group: String?) = ScannedItem(name = "x", amount = amount, group = group)
        val transactions = listOf(
            spend(5000, LocalDate.of(2026, 10, 2), id = 1),
            spend(3000, LocalDate.of(2026, 10, 9), id = 2),
            spend(2000, LocalDate.of(2026, 9, 20), id = 3),
            spend(9000, LocalDate.of(2026, 10, 10), id = 4), // without receipt
        )
        val receipts = mapOf(
            1L to listOf(item(4000, "produce"), item(1000, "sweets_snacks")),
            2L to listOf(item(3000, "dairy")),
            3L to listOf(item(2000, "sweets_snacks")),
        )
        val overview = BudgetAnalyzer.analyse(
            BudgetInput(
                Ledger(transactions, OwnAccounts(setOf(giro), emptyMap())), listOf(pot), categories,
                emptyList(), emptyList(), meanings, today, receipts = receipts
            )
        )
        val status = overview.status(pot)
        // Only two receipts in October, so September is added: 3000 of 10000
        assertEquals(3, status.scannedPurchases)
        assertEquals(3000, status.spent)
        assertEquals(1000, status.planned)
        assertEquals(0.3f, status.share!!, 0.001f)
        assertTrue(status.isOverspent)
        assertEquals(listOf(1L, 3L), status.transactionIds)
        assertEquals(0, overview.frame.planned)
    }
}
