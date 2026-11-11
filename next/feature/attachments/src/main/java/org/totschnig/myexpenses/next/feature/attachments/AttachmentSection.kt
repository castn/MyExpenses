package org.totschnig.myexpenses.next.feature.attachments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.ActionSheet
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.BannerStyle
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.FileRow
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.SheetAction
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.attachments.Attachment
import timber.log.Timber

/** Size of the previews in the rows, see [FileRow] */
private val PreviewSize = 48.dp

/**
 * Photos and files attached to a transaction in its details: tapping one opens it in another app,
 * the button deletes it after asking. Shown only while there are some or one is being added,
 * adding is offered by [AttachmentChip].
 */
@Composable
fun AttachmentSection(viewModel: AttachmentsViewModel, transaction: ShownTransaction) {
    if (transaction.isSplitPart) return
    val previewSize = with(LocalDensity.current) { PreviewSize.roundToPx() }
    val attachments by remember(transaction.id, previewSize) {
        viewModel.attachments(transaction.id, previewSize)
    }.collectAsStateWithLifecycle(emptyList())
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isAdding = state.addingTo == transaction.id
    val failed = state.failed?.takeIf { it.transactionId == transaction.id }
    var toDelete by remember(transaction.id) { mutableStateOf<Attachment?>(null) }
    val context = LocalContext.current

    if (attachments.isEmpty() && !isAdding && failed == null) return
    SectionTitle(stringResource(R.string.next_attachments_title))
    failed?.let {
        Banner(
            title = stringResource(
                when (it.action) {
                    AttachmentFailure.Action.Add -> R.string.next_attachment_add_failed
                    AttachmentFailure.Action.Delete -> R.string.next_attachment_delete_failed
                }
            ),
            messages = emptyList(),
            style = BannerStyle.Alert
        ) {
            TextButton(onClick = viewModel::dismissFailure) {
                Text(stringResource(R.string.next_receipt_close))
            }
        }
    }
    if (attachments.isNotEmpty() || isAdding) {
        DetailCard {
            attachments.forEachIndexed { index, shown ->
                val attachment = shown.attachment
                FileRow(
                    name = attachment.name,
                    preview = shown.preview,
                    icon = attachment.icon,
                    openLabel = stringResource(R.string.next_attachment_open),
                    onClick = { context.view(viewModel, attachment) },
                    removeLabel = stringResource(R.string.next_attachment_delete),
                    onRemove = { toDelete = attachment },
                    isFirst = index == 0
                )
            }
            if (isAdding) {
                ProgressRow(
                    stringResource(R.string.next_attachment_adding),
                    isFirst = attachments.isEmpty()
                )
            }
        }
    }
    toDelete?.let { attachment ->
        ConfirmDialog(
            title = stringResource(R.string.next_attachment_delete),
            text = stringResource(R.string.next_attachment_delete_text, attachment.name),
            confirmLabel = stringResource(R.string.next_attachment_delete),
            onConfirm = {
                viewModel.delete(transaction.id, attachment)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/**
 * Chip for the [org.totschnig.myexpenses.designsystem.ActionChipRow] below the details of a
 * transaction: takes a photo or chooses a file to attach
 */
@Composable
fun AttachmentChip(viewModel: AttachmentsViewModel, transaction: ShownTransaction) {
    if (transaction.isSplitPart) return
    var choose by rememberSaveable { mutableStateOf(false) }
    val camera =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture(),
            viewModel::photoTaken
        )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.add(transaction.id, it) }
    }
    ActionChip(
        text = stringResource(R.string.next_attachment_add),
        icon = Icons.Outlined.AttachFile,
        onClick = { choose = true }
    )
    if (!choose) return
    ActionSheet(
        actions = listOf(
            SheetAction(
                stringResource(R.string.next_attachment_photo),
                icon = { rememberVectorPainter(Icons.Outlined.PhotoCamera) }
            ) {
                launchOrLog(onMissing = { viewModel.photoTaken(false) }) {
                    camera.launch(viewModel.newPhoto(transaction.id))
                }
            },
            SheetAction(
                stringResource(R.string.next_attachment_file),
                icon = { rememberVectorPainter(Icons.Outlined.Description) }
            ) {
                launchOrLog { picker.launch(viewModel.pickableTypes()) }
            }
        ),
        onDismiss = { choose = false },
        title = stringResource(R.string.next_attachment_add)
    )
}

/** Starts another app, e.g. the camera, if there is one */
private fun launchOrLog(onMissing: () -> Unit = {}, launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        Timber.w(e)
        onMissing()
    }
}

private fun Context.view(viewModel: AttachmentsViewModel, attachment: Attachment) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewModel.viewableUri(attachment.uri), attachment.type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (e: ActivityNotFoundException) {
        Timber.w(e)
    } catch (e: IllegalArgumentException) {
        // The file is gone or cannot be shared
        Timber.w(e)
    }
}

/** The icon of the kind of file, shown until a preview is loaded and for files without one */
private val Attachment.icon
    get() = when {
        isImage -> Icons.Outlined.Image
        type == "application/pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.Description
    }
