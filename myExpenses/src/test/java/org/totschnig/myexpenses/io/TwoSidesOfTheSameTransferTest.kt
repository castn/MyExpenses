package org.totschnig.myexpenses.io

import com.google.common.truth.Truth
import junit.framework.TestCase
import org.totschnig.myexpenses.io.ImportUtils.twoSidesOfTheSameTransfer
import java.math.BigDecimal
import java.util.Date

class TwoSidesOfTheSameTransferTest : TestCase() {


    fun testShouldMatchTwoSidesOfSameTransfer() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = "Konto 1")
        val toAccount = ImportAccount(memo = "Konto 2")
        val fromTransaction = ImportTransaction.Builder()
            .toAccount(toAccount.memo)
            .date(now)
            .amount(BigDecimal(5))
            .build()!!
        val toTransaction = ImportTransaction.Builder()
            .toAccount(fromAccount.memo)
            .date(now)
            .amount(BigDecimal(-5))
            .build()!!
        Truth.assertThat(twoSidesOfTheSameTransfer(
            fromAccount,
            fromTransaction,
            toAccount,
            toTransaction
        )).isTrue()
    }

    fun testShouldDistinguishNonMatchingDates() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = "Konto 1")
        val toAccount = ImportAccount(memo = "Konto 2")
        val fromTransaction = ImportTransaction.Builder()
            .toAccount(toAccount.memo)
            .date(now)
            .amount(BigDecimal(5))
            .build()!!
        val toTransaction = ImportTransaction.Builder()
            .toAccount(fromAccount.memo)
            .date(Date(System.currentTimeMillis() - 100000))
            .amount(BigDecimal(-5))
            .build()!!
        Truth.assertThat(twoSidesOfTheSameTransfer(
            fromAccount,
            fromTransaction,
            toAccount,
            toTransaction
        )).isFalse()
    }

    fun testShouldDistinguishNonMatchingAccounts() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = "Konto 1")
        val toAccount = ImportAccount(memo = "Konto 2")
        val fromTransaction = ImportTransaction.Builder()
            .toAccount(toAccount.memo)
            .date(now)
            .amount(BigDecimal(5))
            .build()!!
        val toTransaction = ImportTransaction.Builder()
            .toAccount("Konto 3")
            .date(now)
            .amount(BigDecimal(-5))
            .build()!!
        Truth.assertThat(twoSidesOfTheSameTransfer(
            fromAccount,
            fromTransaction,
            toAccount,
            toTransaction
        )).isFalse()
    }
}
