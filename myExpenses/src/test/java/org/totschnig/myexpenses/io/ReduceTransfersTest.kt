package org.totschnig.myexpenses.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

const val ACCOUNT_1 = "Konto 1"
const val ACCOUNT_2 = "Konto 2"

class ReduceTransfersTest {

    @Test
    fun transformUnknownTransfer() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = ACCOUNT_1,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_2)
                    .date(now)
                    .amount(BigDecimal(-5)).build()!!
            ))
        val reduced = ImportUtils.reduceTransfers(listOf(fromAccount))
        assertThat(reduced[0].transactions.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].isTransfer).isFalse()
    }

    @Test
    fun transformUnknownTransferInSplit() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = ACCOUNT_1,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_2)
                    .date(now)
                    .amount(BigDecimal(-5))
                    .addSplit(ImportTransaction.Builder()
                        .toAccount(ACCOUNT_2)
                        .date(now)
                        .amount(BigDecimal(-5)))
                    .build()!!
            ))
        val reduced = ImportUtils.reduceTransfers(listOf(fromAccount))
        assertThat(reduced[0].transactions.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits).isNotNull()
        assertThat(reduced[0].transactions[0].splits!!.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits!![0].isTransfer).isFalse()
    }

    @Test
    fun reduceTransferBetweenMainTransactions() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = ACCOUNT_1,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_2)
                    .date(now)
                    .amount(BigDecimal(-5)).build()!!
            ))
        val toAccount = ImportAccount(memo = ACCOUNT_2,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_1)
                    .date(now)
                    .amount(BigDecimal(5)).build()!!
            ))
        val reduced = ImportUtils.reduceTransfers(listOf(fromAccount, toAccount))
        assertThat(reduced[0].transactions.size).isEqualTo(0)
        assertThat(reduced[1].transactions.size).isEqualTo(1)
        assertThat(reduced[1].transactions[0].isTransfer).isTrue()
    }

    @Test
    fun reduceTransferWithSplitTransaction() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = ACCOUNT_1,
            transactions = listOf(
                ImportTransaction.Builder()
                    .date(now)
                    .amount(BigDecimal(-5))
                    .addSplit(ImportTransaction.Builder()
                        .toAccount(ACCOUNT_2)
                        .date(now)
                        .amount(BigDecimal(-5)))
                    .build()!!
            ))
        val toAccount = ImportAccount(memo = ACCOUNT_2,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_1)
                    .date(now)
                    .amount(BigDecimal(5)).build()!!
            ))
        val reduced = ImportUtils.reduceTransfers(listOf(fromAccount, toAccount))
        assertThat(reduced[0].transactions.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits).isNotNull()
        assertThat(reduced[0].transactions[0].splits!!.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits!![0].isTransfer).isTrue()
        assertThat(reduced[0].transactions[0].splits!![0].toAmount).isNotNull()
        assertThat(reduced[1].transactions.size).isEqualTo(0)
    }

    @Test
    fun reduceTransferWithSplitTransactionInverse() {
        val now = Date(System.currentTimeMillis())
        val fromAccount = ImportAccount(memo = ACCOUNT_1,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_2)
                    .date(now)
                    .amount(BigDecimal(5))
                    .addSplit(ImportTransaction.Builder()
                        .toAccount(ACCOUNT_2)
                        .date(now)
                        .amount(BigDecimal(5)))
                    .build()!!
            ))
        val toAccount = ImportAccount(memo = ACCOUNT_2,
            transactions = listOf(
                ImportTransaction.Builder()
                    .toAccount(ACCOUNT_1)
                    .date(now)
                    .amount(BigDecimal(-5)).build()!!
            ))
        val reduced = ImportUtils.reduceTransfers(listOf(fromAccount, toAccount))
        assertThat(reduced[0].transactions.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits).isNotNull()
        assertThat(reduced[0].transactions[0].splits!!.size).isEqualTo(1)
        assertThat(reduced[0].transactions[0].splits!![0].isTransfer).isTrue()
        assertThat(reduced[1].transactions.size).isEqualTo(0)
    }
}
