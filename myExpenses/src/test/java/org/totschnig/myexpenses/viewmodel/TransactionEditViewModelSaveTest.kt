package org.totschnig.myexpenses.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.loadActiveTagsForAccount
import org.totschnig.myexpenses.db2.loadTagsForTransaction
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.db2.saveActiveTagsForAccount
import org.totschnig.myexpenses.db2.setAccountProperty
import org.totschnig.myexpenses.db2.writeTag
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.KEY_LAST_USED
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.ui.DisplayParty
import org.totschnig.myexpenses.viewmodel.data.Tag
import org.totschnig.myexpenses.viewmodel.data.TransactionEditData

/**
 * The rules of the classic form for saving an expense or income that the new form of ui_next
 * keeps (P7a1): a payee typed by name, the tags, the account used last, the tags of the account and
 * no transactions in a closed account. Pins what [TransactionEditViewModel] does today, before the
 * classic form goes.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionEditViewModelSaveTest : BaseTestWithRepository() {

    private fun viewModel() = TransactionEditViewModel(application, SavedStateHandle()).also {
        application.injector.inject(it)
    }

    private fun expense(
        accountId: Long,
        amount: Long,
        payee: String? = null,
        tags: List<Tag> = emptyList()
    ) = TransactionEditData(
        uuid = generateUuid(),
        amount = Money(currencyContext.homeCurrencyUnit, amount),
        accountId = accountId,
        party = payee?.let { DisplayParty(null, it) },
        tags = tags,
        comment = "Brötchen"
    )

    @Test
    fun payeeTypedByNameIsFoundOrCreatedOnce() = runBlocking<Unit> {
        val accountId = insertAccount("Bargeld")
        val viewModel = viewModel()
        val first = viewModel.save(expense(accountId, -250, "Bäcker"), null).getOrThrow()
        val second = viewModel.save(expense(accountId, -180, "Bäcker"), null).getOrThrow()
        val payeeId = repository.loadTransaction(first.id).data.payeeId
        assertThat(payeeId).isNotNull()
        assertThat(repository.loadTransaction(second.id).data.payeeId).isEqualTo(payeeId)
        assertThat(repository.requireParty("Bäcker")).isEqualTo(payeeId)
    }

    @Test
    fun newTransactionGetsAmountCommentAndTags() = runBlocking<Unit> {
        val accountId = insertAccount("Bargeld")
        val tag = Tag(repository.writeTag("Urlaub"), "Urlaub")
        val result = viewModel().save(
            expense(accountId, -250, tags = listOf(tag)),
            null
        ).getOrThrow()
        val saved = repository.loadTransaction(result.id).data
        assertThat(saved.amount).isEqualTo(-250)
        assertThat(saved.comment).isEqualTo("Brötchen")
        assertThat(saved.payeeId).isNull()
        assertThat(
            repository.loadTagsForTransaction(result.id).map {
                it.id
            }
        ).containsExactly(tag.id)
    }

    @Test
    fun changingATransactionKeepsItsUuid() = runBlocking<Unit> {
        val accountId = insertAccount("Bargeld")
        val viewModel = viewModel()
        val data = expense(accountId, -250, "Bäcker")
        val id = viewModel.save(data, null).getOrThrow().id
        viewModel.save(
            data.copy(id = id, amount = Money(currencyContext.homeCurrencyUnit, -300)),
            null
        ).getOrThrow()
        val saved = repository.loadTransaction(id).data
        assertThat(saved.amount).isEqualTo(-300)
        assertThat(saved.uuid).isEqualTo(data.uuid)
    }

    @Test
    fun newTransactionWithoutAccountTakesTheOpenOneUsedLast() = runBlocking<Unit> {
        val older = insertAccount("Giro")
        val closed = insertAccount("Alt")
        repository.setAccountProperty(older, KEY_LAST_USED, 1000L)
        repository.setAccountProperty(closed, KEY_LAST_USED, 2000L)
        repository.setAccountProperty(closed, KEY_SEALED, true)
        assertThat(viewModel().newTransaction(0L, null, null)!!.accountId).isEqualTo(older)
    }

    @Test
    fun newTransactionGetsTheTagsOfItsAccount() {
        val accountId = insertAccount("Bargeld")
        val tagId = repository.writeTag("Haushalt")
        repository.saveActiveTagsForAccount(listOf(tagId), accountId)
        assertThat(
            repository.loadActiveTagsForAccount(accountId).map {
                it.id
            }
        ).containsExactly(tagId)
    }

    @Test
    fun nothingIsSavedInAClosedAccount() = runBlocking<Unit> {
        val accountId = insertAccount("Alt")
        repository.setAccountProperty(accountId, KEY_SEALED, true)
        assertThat(viewModel().save(expense(accountId, -250), null).isFailure).isTrue()
    }
}
