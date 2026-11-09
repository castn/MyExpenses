package org.totschnig.myexpenses.viewmodel

import android.content.ContentUris
import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.checkSealedStatus
import org.totschnig.myexpenses.db2.createParty
import org.totschnig.myexpenses.db2.deleteParty
import org.totschnig.myexpenses.db2.insertTemplate
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.db2.loadTemplate
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.markAsExported
import org.totschnig.myexpenses.db2.transactionExists
import org.totschnig.myexpenses.db2.updateTransaction
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_EQUIVALENT_AMOUNT
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.STATUS_EXPORTED
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.epoch2LocalDate

/**
 * Pins how debts are stored: saving, sealing and deleting them, linking transactions and templates,
 * and what the sealed state of a debt refuses.
 */
@RunWith(AndroidJUnit4::class)
class DebtPersistenceTest : BaseDebtTest() {

    private val accountId by lazy { insertAccount("Cash", currency = HOME) }

    private fun linkOf(id: Long) = repository.loadTransaction(id).data.debtId

    private fun amountOf(id: Long) = repository.loadTransaction(id).data.amount

    // Saving

    @Test
    fun saveDebtWithoutIdInsertsARow() {
        val id = saveDebt(
            amount = 10000,
            label = "Car",
            description = "For the car",
            date = LocalDate.of(2025, 3, 1)
        )
        assertThat(id).isGreaterThan(0)
        val saved = displayDebt(id)
        assertThat(saved.label).isEqualTo("Car")
        assertThat(saved.description).isEqualTo("For the car")
        assertThat(saved.payeeId).isEqualTo(payee)
        assertThat(saved.payeeName).isEqualTo("Alice")
        assertThat(saved.amount).isEqualTo(10000)
        assertThat(saved.currency.code).isEqualTo(HOME)
        assertThat(epoch2LocalDate(saved.date)).isEqualTo(LocalDate.of(2025, 3, 1))
        assertThat(saved.isSealed).isFalse()
        assertThat(saved.equivalentAmount).isNull()
    }

    @Test
    fun saveDebtWithIdUpdatesTheRowButNotThePayee() {
        val id = saveDebt(amount = 10000, label = "Car")
        val otherPayee = repository.createParty("Bob")!!.id
        val returned = repository.saveDebt(
            debt(
                id = id,
                amount = -500,
                currency = FOREIGN,
                label = "Bike",
                description = "Changed",
                payeeId = otherPayee,
                equivalentAmount = -1000,
                date = LocalDate.of(2025, 4, 2)
            )
        )
        assertThat(returned).isEqualTo(id)
        val saved = displayDebt(id)
        assertThat(saved.label).isEqualTo("Bike")
        assertThat(saved.description).isEqualTo("Changed")
        assertThat(saved.amount).isEqualTo(-500)
        assertThat(saved.currency.code).isEqualTo(FOREIGN)
        assertThat(saved.equivalentAmount).isEqualTo(-1000)
        assertThat(epoch2LocalDate(saved.date)).isEqualTo(LocalDate.of(2025, 4, 2))
        assertThat(saved.payeeId).isEqualTo(payee)
    }

    @Test
    fun editingAForeignDebtToHomeCurrencyKeepsTheOldEquivalentAmount() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2000)
        repository.saveDebt(debt(id = id, amount = 1500, currency = HOME))
        val saved = displayDebt(id)
        // Looks like a bug: a null equivalent amount is not written, so a debt now held in home
        // currency keeps the old value and reports a balance that is not its amount.
        assertThat(saved.equivalentAmount).isEqualTo(2000)
        assertThat(saved.currentBalance).isEqualTo(1500)
        assertThat(saved.currentEquivalentBalance).isEqualTo(2000)
    }

    @Test
    fun saveDebtThroughTheViewModelEmitsTheId() {
        val id = viewModel.saveDebt(debt(amount = 100)).await()
        assertThat(displayDebt(id).amount).isEqualTo(100)
    }

    // Sealing

    @Test
    fun closeAndReopenToggleTheSealedFlag() {
        val id = saveDebt(amount = 100)
        viewModel.closeDebt(id)
        assertThat(displayDebt(id).isSealed).isTrue()
        viewModel.reopenDebt(id)
        assertThat(displayDebt(id).isSealed).isFalse()
    }

    @Test
    fun sealedDebtRefusesAnEdit() {
        val id = saveDebt(amount = 100, label = "Before")
        viewModel.closeDebt(id)
        assertThrows(SQLiteConstraintException::class.java) {
            repository.saveDebt(debt(id = id, amount = 200, label = "After"))
        }
        assertThat(displayDebt(id).label).isEqualTo("Before")
        assertThat(displayDebt(id).amount).isEqualTo(100)
    }

    @Test
    fun reopenedDebtCanBeEditedAgain() {
        val id = saveDebt(amount = 100)
        viewModel.closeDebt(id)
        viewModel.reopenDebt(id)
        repository.saveDebt(debt(id = id, amount = 200))
        assertThat(displayDebt(id).amount).isEqualTo(200)
    }

    @Test
    fun sealedDebtAcceptsAChangeOfTheEquivalentAmountAlone() {
        val id = saveDebt(amount = 100, currency = FOREIGN, equivalentAmount = 200)
        viewModel.closeDebt(id)
        val count = contentResolver.update(
            debtUri(id),
            ContentValues(1).apply { put(KEY_EQUIVALENT_AMOUNT, 300) },
            null,
            null
        )
        // Looks like a bug: the trigger lists date, label, amount, currency and description, but
        // not the equivalent amount, so the home value of a sealed debt can still change.
        assertThat(count).isEqualTo(1)
        assertThat(displayDebt(id).equivalentAmount).isEqualTo(300)
    }

    // Linking transactions

    @Test
    fun transactionKeepsItsLink() {
        val id = saveDebt(amount = 100)
        assertThat(linkOf(insertLinked(accountId, 50, id))).isEqualTo(id)
    }

    @Test
    fun updateCanLinkMoveAndUnlink() {
        val first = saveDebt(amount = 100, label = "First")
        val second = saveDebt(amount = 100, label = "Second")
        val transactionId = insertLinked(accountId, 50, null)
        fun setLink(debtId: Long?) = repository.updateTransaction(
            repository.loadTransaction(transactionId).data.copy(debtId = debtId)
        )

        setLink(first)
        assertThat(linkOf(transactionId)).isEqualTo(first)
        setLink(second)
        assertThat(linkOf(transactionId)).isEqualTo(second)
        setLink(null)
        assertThat(linkOf(transactionId)).isNull()
    }

    @Test
    fun aLinkThatIsNotPositiveIsStoredAsNoLink() {
        assertThat(linkOf(insertLinked(accountId, 50, 0))).isNull()
    }

    @Test
    fun sealedDebtRefusesANewTransaction() {
        val id = saveDebt(amount = 100)
        viewModel.closeDebt(id)
        assertThrows(SQLiteConstraintException::class.java) {
            insertLinked(accountId, 50, id)
        }
    }

    @Test
    fun sealedDebtRefusesAChangeOfALinkedTransaction() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        viewModel.closeDebt(id)
        assertThrows(SQLiteConstraintException::class.java) {
            contentResolver.update(
                ContentUris.withAppendedId(TransactionProvider.TRANSACTIONS_URI, transactionId),
                ContentValues(1).apply { put(KEY_AMOUNT, 60) },
                null,
                null
            )
        }
        assertThat(amountOf(transactionId)).isEqualTo(50)
    }

    @Test
    fun sealedDebtRefusesLosingOrGainingALink() {
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val open = saveDebt(amount = 100, label = "Open")
        val onSealed = insertLinked(accountId, 50, sealed)
        val onOpen = insertLinked(accountId, 50, open)
        viewModel.closeDebt(sealed)

        assertThrows(SQLiteConstraintException::class.java) {
            repository.updateTransaction(
                repository.loadTransaction(onSealed).data.copy(debtId = null)
            )
        }
        assertThrows(SQLiteConstraintException::class.java) {
            repository.updateTransaction(
                repository.loadTransaction(onOpen).data.copy(debtId = sealed)
            )
        }
        assertThat(linkOf(onSealed)).isEqualTo(sealed)
        assertThat(linkOf(onOpen)).isEqualTo(open)
    }

    @Test
    fun sealedDebtRefusesDeletingOrVoidingALinkedTransaction() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        viewModel.closeDebt(id)
        assertThrows(SQLiteConstraintException::class.java) {
            repository.deleteTransaction(transactionId)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            repository.deleteTransaction(transactionId, markAsVoid = true)
        }
        assertThat(repository.transactionExists(transactionId)).isTrue()
    }

    @Test
    fun checkSealedStatusSeesASealedDebt() = runBlocking {
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val open = saveDebt(amount = 100, label = "Open")
        val onSealed = insertLinked(accountId, 50, sealed)
        val onOpen = insertLinked(accountId, 50, open)
        viewModel.closeDebt(sealed)

        fun status(id: Long) = runBlocking {
            repository.checkSealedStatus(listOf(id), withTransfer = false).getOrThrow()
        }
        assertThat(status(onSealed).hasSealedDebt).isTrue()
        assertThat(status(onSealed).hasSealedAccount).isFalse()
        assertThat(status(onOpen).hasSealedDebt).isFalse()
        assertThat(repository.checkSealedStatus(accountId).getOrThrow().hasSealedDebt).isTrue()
    }

    @Test
    fun checkSealedStatusOfASplitSeesASealedDebtOnAPart() = runBlocking {
        val id = saveDebt(amount = 100)
        val split = insertSplitWithLinkedPart(accountId, id)
        viewModel.closeDebt(id)
        val status = repository.checkSealedStatus(listOf(split), withTransfer = false)
        assertThat(status.getOrThrow().hasSealedDebt).isTrue()
    }

    @Test
    fun markAsExportedPassesASealedDebtAndLeavesItSealed() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        viewModel.closeDebt(id)
        repository.markAsExported(accountId, null)
        contentResolver.query(
            ContentUris.withAppendedId(TransactionProvider.TRANSACTIONS_URI, transactionId),
            arrayOf(KEY_STATUS),
            null,
            null,
            null
        )!!.use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(STATUS_EXPORTED)
        }
        assertThat(displayDebt(id).isSealed).isTrue()
    }

    // Deleting

    @Test
    fun deleteDebtKeepsTransactionsAndUnlinksThem() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        assertThat(viewModel.deleteDebt(id).await()).isTrue()
        assertThat(loadDebts(showSealed = true)).isEmpty()
        assertThat(linkOf(transactionId)).isNull()
        assertThat(amountOf(transactionId)).isEqualTo(50)
    }

    @Test
    fun deleteDebtOfUnknownIdReportsFalse() {
        assertThat(viewModel.deleteDebt(4711).await()).isFalse()
    }

    @Test
    fun sealedDebtCanBeDeletedAndItsTransactionsAreUnlinked() {
        val id = saveDebt(amount = 100, label = "Sealed")
        val other = saveDebt(amount = 100, label = "Other sealed")
        val transactionId = insertLinked(accountId, 50, id)
        val otherTransactionId = insertLinked(accountId, 50, other)
        viewModel.closeDebt(id)
        viewModel.closeDebt(other)

        assertThat(viewModel.deleteDebt(id).await()).isTrue()

        assertThat(linkOf(transactionId)).isNull()
        assertThat(linkOf(otherTransactionId)).isEqualTo(other)
        assertThat(displayDebt(other).isSealed).isTrue()
    }

    @Test
    fun deleteDebtUnlinksTransactionsOfASealedAccountAndLeavesItSealed() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        val accountUri = ContentUris.withAppendedId(TransactionProvider.ACCOUNTS_URI, accountId)
        contentResolver.update(
            accountUri,
            ContentValues(1).apply { put(KEY_SEALED, 1) },
            null,
            null
        )

        assertThat(viewModel.deleteDebt(id).await()).isTrue()

        assertThat(linkOf(transactionId)).isNull()
        contentResolver.query(accountUri, arrayOf(KEY_SEALED), null, null, null)!!.use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun deleteAccountWithTransactionsOnASealedDebtIsRefused() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        viewModel.closeDebt(id)
        val result = viewModel.deleteAccountsInternal(longArrayOf(accountId))
        assertThat(result.exceptionOrNull()).isInstanceOf(AccountSealedException::class.java)
        assertThat(repository.transactionExists(transactionId)).isTrue()
    }

    @Test
    fun deleteAccountLeavesAnOpenDebtWithoutItsTransactions() {
        val id = saveDebt(amount = 100)
        insertLinked(accountId, 50, id)
        assertThat(displayDebt(id).sum).isEqualTo(50)
        assertThat(viewModel.deleteAccountsInternal(longArrayOf(accountId)).isSuccess).isTrue()
        assertThat(displayDebt(id).sum).isEqualTo(0)
    }

    // Payees

    @Test
    fun deletingThePayeeDeletesItsDebtsEvenSealedAndUnlinksTheTransactions() {
        val id = saveDebt(amount = 100)
        val transactionId = insertLinked(accountId, 50, id)
        viewModel.closeDebt(id)
        repository.deleteParty(payee)
        assertThat(loadDebts(showSealed = true)).isEmpty()
        assertThat(linkOf(transactionId)).isNull()
    }

    @Test
    fun mergingPartiesMovesDebtsAndKeepsThemSealed() {
        val keep = repository.createParty("Bob")!!.id
        val open = saveDebt(amount = 100, label = "Open")
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val transactionId = insertLinked(accountId, 50, sealed)
        viewModel.closeDebt(sealed)

        PartyListViewModel(application, SavedStateHandle())
            .also { application.injector.inject(it) }
            .mergeParties(setOf(payee), keep, MergeStrategy.DELETE)

        assertThat(displayDebt(open).payeeId).isEqualTo(keep)
        assertThat(displayDebt(sealed).payeeId).isEqualTo(keep)
        assertThat(displayDebt(open).isSealed).isFalse()
        assertThat(displayDebt(sealed).isSealed).isTrue()
        assertThat(linkOf(transactionId)).isEqualTo(sealed)
    }

    // Templates

    @Test
    fun templateKeepsItsLinkAndLosesItWithTheDebt() {
        val id = saveDebt(amount = 100)
        val templateId = repository.insertTemplate(
            title = "Rate",
            accountId = accountId,
            amount = 10,
            debtId = id
        ).data.id
        assertThat(repository.loadTemplate(templateId)!!.data.debtId).isEqualTo(id)
        assertThat(viewModel.deleteDebt(id).await()).isTrue()
        assertThat(repository.loadTemplate(templateId)!!.data.debtId).isNull()
    }

    @Test
    fun templateInstanceIsLinkedToTheDebt() = runBlocking {
        val id = saveDebt(amount = 100)
        val templateId = repository.insertTemplate(
            title = "Rate",
            accountId = accountId,
            amount = 10,
            debtId = id
        ).data.id
        val instance = repository.instantiateTemplate(
            exchangeRateHandler,
            templateId,
            currencyContext
        ).getOrThrow()
        assertThat(instance.data.debtId).isEqualTo(id)
        assertThat(displayDebt(id).sum).isEqualTo(10)
    }

    @Test
    fun templateOfASealedDebtCannotBeInstantiated() {
        val id = saveDebt(amount = 100)
        val templateId = repository.insertTemplate(
            title = "Rate",
            accountId = accountId,
            amount = 10,
            debtId = id
        ).data.id
        viewModel.closeDebt(id)
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                repository.instantiateTemplate(
                    exchangeRateHandler,
                    templateId,
                    currencyContext
                )
            }
        }
    }
}
