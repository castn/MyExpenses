package org.totschnig.myexpenses.db2

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.KEY_SEALED

/**
 * The rules for saving an expense or income that the classic form had and the form of the new UI
 * keeps, pinned in P7a1 on the classic view model and moved here when it was deleted (P7a3): a
 * payee typed by name, the tags, the tags of an account and no transactions in a closed account.
 * The store of the new UI (`RepositoryTransactionStore`) saves with these functions.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSaveRulesTest : BaseTestWithRepository() {

    private fun expense(
        accountId: Long,
        amount: Long,
        payee: String? = null,
        tagIds: List<Long> = emptyList()
    ) = Transaction(
        accountId = accountId,
        amount = amount,
        payeeId = payee?.let { repository.requireParty(it) },
        tagList = tagIds,
        comment = "Brötchen",
        uuid = generateUuid()
    )

    @Test
    fun payeeTypedByNameIsFoundOrCreatedOnce() {
        val accountId = insertAccount("Bargeld")
        val first = repository.createTransaction(expense(accountId, -250, "Bäcker"))
        val second = repository.createTransaction(expense(accountId, -180, "Bäcker"))
        val payeeId = repository.loadTransaction(first.id).data.payeeId
        assertThat(payeeId).isNotNull()
        assertThat(repository.loadTransaction(second.id).data.payeeId).isEqualTo(payeeId)
    }

    @Test
    fun newTransactionGetsAmountCommentAndTags() {
        val accountId = insertAccount("Bargeld")
        val tagId = repository.writeTag("Urlaub")
        val id = repository.createTransaction(expense(accountId, -250, tagIds = listOf(tagId))).id
        val saved = repository.loadTransaction(id).data
        assertThat(saved.amount).isEqualTo(-250)
        assertThat(saved.comment).isEqualTo("Brötchen")
        assertThat(saved.payeeId).isNull()
        assertThat(repository.loadTagsForTransaction(id).map { it.id }).containsExactly(tagId)
    }

    @Test
    fun changingATransactionKeepsItsUuid() {
        val accountId = insertAccount("Bargeld")
        val data = expense(accountId, -250, "Bäcker")
        val id = repository.createTransaction(data).id
        repository.updateTransaction(repository.loadTransaction(id).data.copy(amount = -300))
        val saved = repository.loadTransaction(id).data
        assertThat(saved.amount).isEqualTo(-300)
        assertThat(saved.uuid).isEqualTo(data.uuid)
    }

    @Test
    fun newTransactionGetsTheTagsOfItsAccount() {
        val accountId = insertAccount("Bargeld")
        val tagId = repository.writeTag("Haushalt")
        repository.saveActiveTagsForAccount(listOf(tagId), accountId)
        val tagIds = repository.loadActiveTagsForAccount(accountId).map { it.id }
        assertThat(tagIds).containsExactly(tagId)
    }

    @Test
    fun nothingIsSavedInAClosedAccount() {
        val accountId = insertAccount("Alt")
        repository.setAccountProperty(accountId, KEY_SEALED, true)
        assertThrows(Exception::class.java) {
            repository.createTransaction(expense(accountId, -250))
        }
    }
}
