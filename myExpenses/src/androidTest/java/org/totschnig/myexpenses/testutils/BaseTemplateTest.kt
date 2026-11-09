package org.totschnig.myexpenses.testutils

import org.totschnig.myexpenses.provider.AccountInfo
import org.totschnig.myexpenses.provider.PayeeInfo
import org.totschnig.myexpenses.provider.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.TABLE_TEMPLATES
import org.totschnig.myexpenses.provider.TemplateInfo
import org.totschnig.myexpenses.provider.insert

abstract class BaseTemplateTest : BaseDbTest() {

    fun insertData() {
        for (transactionInfo in templateInfos) {

            mDb.insert(
                TABLE_TEMPLATES,
                transactionInfo.contentValues
            )
        }
    }

    val testAccountId by lazy {
        mDb.insert(
            TABLE_ACCOUNTS,
            AccountInfo("Test account", cashAccount.id, 0).contentValues
        )
    }
    val templateInfos: Array<TemplateInfo> by lazy {
        val payeeId = mDb.insert(TABLE_PAYEES, PayeeInfo("N.N").contentValues)
        arrayOf(
            TemplateInfo(
                accountId = testAccountId,
                title = "Template monthly",
                amount = 150,
                payeeId = payeeId
            ),
            TemplateInfo(
                accountId = testAccountId,
                title = "Template weekly",
                amount = 200,
                payeeId = payeeId
            ),
            TemplateInfo(
                accountId = testAccountId,
                title = "Template daily",
                amount = 100,
                payeeId = payeeId
            )
        )
    }
}
