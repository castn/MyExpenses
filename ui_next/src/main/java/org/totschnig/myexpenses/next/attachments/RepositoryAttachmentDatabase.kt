package org.totschnig.myexpenses.next.attachments

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.WEBP
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.addAttachments
import org.totschnig.myexpenses.db2.deleteAttachments
import org.totschnig.myexpenses.db2.loadAttachments
import org.totschnig.myexpenses.next.data.attachments.Attachment
import org.totschnig.myexpenses.next.data.attachments.AttachmentDatabase
import org.totschnig.myexpenses.next.data.attachments.AttachmentOrigin
import org.totschnig.myexpenses.next.data.attachments.KeepRule
import org.totschnig.myexpenses.next.data.attachments.keepRuleFor
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.enumValueOrDefault
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_ATTACHMENTS_URI
import org.totschnig.myexpenses.provider.fileName
import org.totschnig.myexpenses.provider.isDebugAsset
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.ImageOptimizer
import org.totschnig.myexpenses.util.PictureDirHelper
import org.totschnig.myexpenses.util.asExtension
import org.totschnig.myexpenses.util.io.FileCopyUtils
import org.totschnig.myexpenses.util.io.getFileExtension
import org.totschnig.myexpenses.util.io.getNameWithoutExtension

/**
 * The attachments of transactions through the [Repository]; files are stored in the pictures of
 * the app as the settings say (copy, reduce photos), like the classic transaction form did
 */
class RepositoryAttachmentDatabase(
    private val application: MyApplication,
    private val repository: Repository,
    private val prefHandler: PrefHandler
) : AttachmentDatabase {
    private val contentResolver get() = repository.contentResolver

    override fun changesOf(transactionId: Long): Flow<Unit> = contentResolver.observeQuery(
        uri = TRANSACTIONS_ATTACHMENTS_URI,
        selection = "$KEY_TRANSACTIONID = ?",
        selectionArgs = arrayOf(transactionId.toString()),
        notifyForDescendants = true
    ).map { }

    override fun load(transactionId: Long) = repository.loadAttachments(transactionId).map { uri ->
        Attachment(
            uri = uri,
            name = runCatching { uri.fileName(application) }.getOrNull()
                ?: uri.lastPathSegment.orEmpty(),
            type = runCatching { contentResolver.getType(uri) }.getOrNull()
        )
    }

    override fun thumbnail(uri: Uri, size: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(uri, Size(size, size), null)
        } else {
            sampledBitmap(uri, size)
        }

    /** Decodes the image reduced by a power of two, so that its shorter side is about [size] */
    private fun sampledBitmap(uri: Uri, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= size) sampleSize *= 2
        return contentResolver.openInputStream(uri)?.use {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    override fun add(transactionId: Long, uri: Uri) {
        repository.addAttachments(transactionId, listOf(keep(uri)))
    }

    override fun delete(transactionId: Long, uri: Uri) {
        repository.deleteAttachments(transactionId, listOf(uri))
    }

    override fun newPhotoUri(): Uri = PictureDirHelper.getOutputMediaUri(true, application)

    override fun viewableUri(uri: Uri): Uri = AppDirHelper.ensureContentUri(uri, application)

    override fun pickableTypes(): List<String> = prefHandler.requireString(
        PrefKey.ATTACHMENT_MIME_TYPES,
        application.getString(R.string.default_attachment_mime_types)
    ).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Where the attachment is stored, see [keepRuleFor]; a new photo is deleted from the cache once
     * kept
     */
    private fun keep(uri: Uri): Uri {
        val home = PictureDirHelper.getPictureUriBase(false, application)
        val temporary = PictureDirHelper.getPictureUriBase(true, application)
        val origin = when {
            uri.isDebugAsset || uri.toString().startsWith(home) -> AttachmentOrigin.Home
            uri.toString().startsWith(temporary) -> AttachmentOrigin.Temporary
            else -> AttachmentOrigin.External
        }
        val rule = keepRuleFor(
            origin,
            isImage = contentResolver.getType(uri)?.startsWith("image") == true,
            copyExternal = prefHandler.getBoolean(PrefKey.COPY_ATTACHMENT, true),
            optimizePhotos = prefHandler.getBoolean(PrefKey.OPTIMIZE_PICTURE, true)
        )
        val kept = when (rule) {
            KeepRule.Keep -> uri

            KeepRule.Optimize -> optimized(uri)

            KeepRule.Copy -> {
                val fileName = uri.fileName(application)
                PictureDirHelper.getOutputMediaUri(
                    false,
                    application,
                    fileName = getNameWithoutExtension(fileName),
                    extension = getFileExtension(fileName)
                ).also { FileCopyUtils.copy(contentResolver, uri, it) }
            }
        }
        if (origin == AttachmentOrigin.Temporary) contentResolver.delete(uri, null, null)
        return kept
    }

    private fun optimized(uri: Uri): Uri {
        val format: Bitmap.CompressFormat =
            prefHandler.enumValueOrDefault(PrefKey.OPTIMIZE_PICTURE_FORMAT, WEBP)
        val maxSize = prefHandler.getInt(PrefKey.OPTIMIZE_PICTURE_MAX_SIZE, DEFAULT_MAX_SIZE)
        val quality = prefHandler.getInt(PrefKey.OPTIMIZE_PICTURE_QUALITY, DEFAULT_QUALITY)
        val target =
            PictureDirHelper.getOutputMediaUri(false, application, extension = format.asExtension)
        return target.also {
            ImageOptimizer.optimize(
                contentResolver,
                uri,
                it,
                format,
                maxSize,
                maxSize,
                quality.coerceIn(QUALITIES)
            )
        }
    }

    private companion object {
        // Defaults of the settings, as in the classic transaction form
        const val DEFAULT_MAX_SIZE = 1000
        const val DEFAULT_QUALITY = 80
        val QUALITIES = 0..100
    }
}
