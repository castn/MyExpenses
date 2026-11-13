package org.totschnig.myexpenses.next.cash

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.countTransactionsPerAccount
import org.totschnig.myexpenses.db2.createAccount
import org.totschnig.myexpenses.db2.deleteAccount
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.next.data.cash.CashWriting

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
}
