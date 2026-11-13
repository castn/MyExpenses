package org.totschnig.myexpenses.next.cash

import android.content.ContentUris
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.countTransactionsPerAccount
import org.totschnig.myexpenses.db2.createAccount
import org.totschnig.myexpenses.db2.deleteAccount
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.db2.getCurrencyForAccount
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.next.data.cash.CashWriting
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.URI_SEGMENT_TRANSFORM_TO_TRANSFER
import org.totschnig.myexpenses.provider.useAndMapToList

/** [CashWriting] through the repository of the app */
class ProviderCashWriting(private val context: Context) : CashWriting {

    private val repository get() = context.injector.repository()

    override suspend fun createAccount(balance: Long): Long = withContext(Dispatchers.IO) {
        repository.createAccount(
            Account(
                label = context.getString(R.string.account_type_cash),
                currency = context.injector.currencyContext().homeCurrencyUnit.code,
                openingBalance = balance,
                type = repository.findAccountType(PREDEFINED_NAME_CASH)
            )
        ).id
    }

    override suspend fun deleteIfEmpty(accountId: Long): Boolean = withContext(Dispatchers.IO) {
        (repository.countTransactionsPerAccount(accountId) == 0).also { isEmpty ->
            if (isEmpty) repository.deleteAccount(accountId)
        }
    }

    override suspend fun moveToAccount(transactionIds: List<Long>, accountId: Long) {
        withContext(Dispatchers.IO) {
            val currency = repository.getCurrencyForAccount(accountId) ?: return@withContext
            val resolver = context.contentResolver
            // A transfer moves the same amount, so only debits in the currency of the cash account
            val accounts = resolver.query(
                TRANSACTIONS_URI,
                arrayOf(KEY_ROWID, KEY_ACCOUNTID),
                "$KEY_ROWID IN (${transactionIds.joinToString()})",
                null,
                null
            )?.useAndMapToList { it.getLong(0) to it.getLong(1) }.orEmpty()
            val sameCurrency = accounts.map { it.second }.distinct()
                .filter { repository.getCurrencyForAccount(it) == currency }
                .toSet()
            accounts.filter { it.second in sameCurrency }.forEach { (transactionId, _) ->
                resolver.insert(
                    ContentUris.appendId(
                        ContentUris.appendId(TRANSACTIONS_URI.buildUpon(), transactionId)
                            .appendPath(URI_SEGMENT_TRANSFORM_TO_TRANSFER),
                        accountId
                    ).build(),
                    null
                )
            }
        }
    }
}
