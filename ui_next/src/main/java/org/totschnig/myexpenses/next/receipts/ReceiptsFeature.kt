package org.totschnig.myexpenses.next.receipts

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.feature.receipts.ReceiptChip
import org.totschnig.myexpenses.next.feature.receipts.ReceiptPhotoLoader
import org.totschnig.myexpenses.next.feature.receipts.ReceiptScanAction
import org.totschnig.myexpenses.next.feature.receipts.ReceiptSection
import org.totschnig.myexpenses.next.feature.receipts.ReceiptViewModel
import org.totschnig.myexpenses.next.feature.receipts.WaitingReceiptsBanner
import org.totschnig.myexpenses.next.feature.receipts.LocalNewTransactionEditor
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Receipts scanned for transactions or before them
 *
 * @param isContractPayment whether a transaction pays a contract, null while not known; contracts
 * are not paid with a receipt
 */
class ReceiptsFeature(
    activity: ComponentActivity,
    data: NextData,
    private val isContractPayment: @Composable (ShownTransaction) -> Boolean? = { false }
) : NextFeature {
    val viewModel by activity.viewModelsOf { savedState ->
        val application = activity.application
        ReceiptViewModel(
            savedState,
            data.receipts.database,
            data.homeCurrency,
            ReceiptPhotoLoader(activity.contentResolver)::load
        ) { activity.injector.ocrFeature()?.let { OcrTextRecognition(application, it) } }
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        ReceiptSection(viewModel, transaction)
    }

    @Composable
    override fun TransactionChips(transaction: ShownTransaction) {
        if (isContractPayment(transaction) == false) {
            ReceiptChip(viewModel, transaction)
        }
    }

    @Composable
    override fun OverviewBanner(navigation: NextNavigation) {
        WaitingReceiptsBanner(viewModel)
    }

    @Composable
    override fun OverviewAction() {
        ReceiptScanAction(viewModel)
    }

    companion object {
        /** Lets the receipt screens open the editor for a new transaction, also outside of the main screen */
        val editor = LocalNewTransactionEditor provides ExpenseEditForReceipt
    }
}
