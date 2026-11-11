package org.totschnig.myexpenses.next.data.attachments

import android.graphics.Bitmap
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * A photo or file attached to a transaction
 *
 * @param name the file name to show, e.g. "receipt_1.webp"
 * @param type the MIME type, null if unknown, e.g. for a file that is gone
 */
data class Attachment(val uri: Uri, val name: String, val type: String?) {
    val isImage get() = type?.startsWith("image/") == true
}

/**
 * The attachments in the database of the app, implemented by the shell. Apart from the flow, the
 * functions block and are called on `Dispatchers.IO`.
 */
interface AttachmentDatabase {
    /** Emits at once and on each change of the attachments of the transaction */
    fun changesOf(transactionId: Long): Flow<Unit>

    fun load(transactionId: Long): List<Attachment>

    /** A small preview of an image, [size] pixels on its longer side, null if there is none */
    fun thumbnail(uri: Uri, size: Int): Bitmap?

    /**
     * Attaches the photo or file, stored as [keepRuleFor] says: copied to the pictures of the app,
     * reduced if it is a photo, or only linked
     */
    fun add(transactionId: Long, uri: Uri)

    /** Removes the attachment from the transaction; the file is deleted once nothing needs it */
    fun delete(transactionId: Long, uri: Uri)

    /** A new file in the cache of the app for the camera to write a photo to */
    fun newPhotoUri(): Uri

    /** The attachment as an uri other apps can open */
    fun viewableUri(uri: Uri): Uri

    /** The MIME types offered when choosing a file, from the settings */
    fun pickableTypes(): List<String>
}

/** Where a photo or file comes from when it is attached */
enum class AttachmentOrigin {
    /** Already in the pictures of the app, e.g. the photo of a receipt */
    Home,

    /** Written by the camera into the cache of the app, deleted once kept */
    Temporary,

    /** From another app, e.g. a file chosen in the documents */
    External
}

/** How an attachment is stored, see [keepRuleFor] */
enum class KeepRule {
    /** Stays where it is: in the pictures of the app, or only linked if copies are switched off */
    Keep,

    /** Copied unchanged to the pictures of the app */
    Copy,

    /** Reduced in size and quality as set in the settings and stored in the pictures of the app */
    Optimize
}

/**
 * How a photo or file from [origin] is stored. Files of other apps are only linked if
 * [copyExternal] is off, photos are reduced if [optimizePhotos] is on. Same rules as the classic
 * transaction form.
 */
fun keepRuleFor(
    origin: AttachmentOrigin,
    isImage: Boolean,
    copyExternal: Boolean,
    optimizePhotos: Boolean
) = when {
    origin == AttachmentOrigin.Home -> KeepRule.Keep
    origin == AttachmentOrigin.External && !copyExternal -> KeepRule.Keep
    isImage && optimizePhotos -> KeepRule.Optimize
    else -> KeepRule.Copy
}

/**
 * Runs [block], null if a file cannot be read or written: it is gone, may not be read, or the
 * storage fails
 */
inline fun <T> storageOrNull(block: () -> T): T? = try {
    block()
} catch (e: IOException) {
    Timber.w(e)
    null
} catch (e: SecurityException) {
    Timber.w(e)
    null
} catch (e: IllegalArgumentException) {
    Timber.w(e)
    null
} catch (e: IllegalStateException) {
    Timber.w(e)
    null
}
