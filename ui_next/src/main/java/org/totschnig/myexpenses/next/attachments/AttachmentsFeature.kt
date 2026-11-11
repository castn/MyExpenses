package org.totschnig.myexpenses.next.attachments

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.attachments.storageOrNull
import org.totschnig.myexpenses.next.feature.attachments.AttachmentChip
import org.totschnig.myexpenses.next.feature.attachments.AttachmentSection
import org.totschnig.myexpenses.next.feature.attachments.AttachmentsViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/** Photos and files attached to transactions, in their details */
class AttachmentsFeature(activity: ComponentActivity, private val data: NextData) : NextFeature {
    private val database by lazy {
        val injector = activity.injector
        RepositoryAttachmentDatabase(
            activity.application as MyApplication,
            injector.repository(),
            injector.prefHandler()
        )
    }

    val viewModel by activity.viewModelsOf { AttachmentsViewModel(it, database) }

    /**
     * Attaches [uri] to the transaction in the scope of [NextData], so that it is also stored
     * when the screen that saved the transaction closes at once, e.g. the photo of a scan
     */
    fun attach(transactionId: Long, uri: Uri) {
        data.scope.launch(Dispatchers.IO) {
            storageOrNull { database.add(transactionId, uri) }
        }
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        AttachmentSection(viewModel, transaction)
    }

    @Composable
    override fun TransactionChips(transaction: ShownTransaction) {
        AttachmentChip(viewModel, transaction)
    }
}
