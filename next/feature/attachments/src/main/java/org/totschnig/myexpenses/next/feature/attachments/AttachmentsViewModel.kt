package org.totschnig.myexpenses.next.feature.attachments

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.attachments.Attachment
import org.totschnig.myexpenses.next.data.attachments.AttachmentDatabase
import org.totschnig.myexpenses.next.data.attachments.storageOrNull
import org.totschnig.myexpenses.next.feature.attachments.AttachmentFailure.Action

/** An attachment with its preview, null for files that are no image or cannot be read */
data class ShownAttachment(val attachment: Attachment, val preview: ImageBitmap?)

/**
 * What is being done with the attachments of a transaction
 *
 * @param addingTo the transaction a photo or file is being stored for
 * @param failed the transaction for which adding or deleting failed, until it is dismissed
 */
data class AttachmentsState(val addingTo: Long? = null, val failed: AttachmentFailure? = null)

data class AttachmentFailure(val transactionId: Long, val action: Action) {
    enum class Action { Add, Delete }
}

/** What can be done with the attachments in the details of a transaction */
interface AttachmentActions {
    fun add(transactionId: Long, uri: Uri)

    fun delete(transactionId: Long, attachment: Attachment)

    /**
     * A new file for the camera to write a photo for the transaction to; remembered, also when the
     * app is ended while the camera is open, until [photoTaken]
     */
    fun newPhoto(transactionId: Long): Uri

    /** The camera is done, attaches the photo if it took one */
    fun photoTaken(success: Boolean)

    fun dismissFailure()
}

/**
 * The attachments of transactions in their details: photos and files, which are viewed by other
 * apps, see [viewableUri]
 */
class AttachmentsViewModel(
    private val savedState: SavedStateHandle,
    private val database: AttachmentDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    AttachmentActions {

    private val _state = MutableStateFlow(AttachmentsState())
    val state: StateFlow<AttachmentsState> = _state.asStateFlow()

    /**
     * The attachments of the transaction, with previews [previewSize] pixels large; again after
     * each change
     */
    fun attachments(transactionId: Long, previewSize: Int): Flow<List<ShownAttachment>> =
        database.changesOf(transactionId).map {
            database.load(transactionId).map { attachment ->
                val preview = if (attachment.isImage) preview(attachment.uri, previewSize) else null
                ShownAttachment(attachment, preview)
            }
        }.flowOn(io)

    private fun preview(uri: Uri, size: Int): ImageBitmap? =
        storageOrNull { database.thumbnail(uri, size) }?.asImageBitmap()

    fun viewableUri(uri: Uri): Uri = database.viewableUri(uri)

    fun pickableTypes(): Array<String> = database.pickableTypes().toTypedArray()

    override fun add(transactionId: Long, uri: Uri) {
        _state.update { it.copy(addingTo = transactionId, failed = null) }
        viewModelScope.launch(io) {
            val isAdded = storageOrNull { database.add(transactionId, uri) } != null
            val failed = if (isAdded) null else AttachmentFailure(transactionId, Action.Add)
            _state.update { it.copy(addingTo = null, failed = failed) }
        }
    }

    override fun delete(transactionId: Long, attachment: Attachment) {
        _state.update { it.copy(failed = null) }
        viewModelScope.launch(io) {
            if (storageOrNull { database.delete(transactionId, attachment.uri) } == null) {
                val failed = AttachmentFailure(transactionId, Action.Delete)
                _state.update { it.copy(failed = failed) }
            }
        }
    }

    override fun newPhoto(transactionId: Long): Uri = database.newPhotoUri().also {
        savedState[KEY_PHOTO] = it
        savedState[KEY_PHOTO_FOR] = transactionId
    }

    override fun photoTaken(success: Boolean) {
        val photo = savedState.remove<Uri>(KEY_PHOTO)
        val transactionId = savedState.remove<Long>(KEY_PHOTO_FOR)
        if (success && photo != null && transactionId != null) add(transactionId, photo)
    }

    override fun dismissFailure() {
        _state.update { it.copy(failed = null) }
    }

    private companion object {
        const val KEY_PHOTO = "attachmentPhoto"
        const val KEY_PHOTO_FOR = "attachmentPhotoFor"
    }
}
