package org.totschnig.myexpenses.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File

/**
 * A local folder served like a storage access framework tree, so that the real
 * [StorageAccessFrameworkBackendProvider] can run on it in place of a cloud folder.
 */
class LocalDocumentsProvider : ContentProvider() {
    lateinit var root: File

    /** Some providers report no size for folders, the local storage provider does. */
    var reportFolderSize = true

    val treeUri: Uri
        get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)

    private fun fileFor(documentId: String) = File(root, documentId.removePrefix(ROOT_ID))

    private fun idFor(file: File) = ROOT_ID + file.path.removePrefix(root.path)

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE
        )
        val file = fileFor(DocumentsContract.getDocumentId(uri))
        val files = if (uri.lastPathSegment == "children") {
            file.listFiles().orEmpty().sortedBy { it.name }
        } else {
            listOf(file)
        }
        return MatrixCursor(columns).apply {
            files.forEach { child -> addRow(columns.map { valueOf(child, it) }) }
        }
    }

    private fun valueOf(file: File, column: String): Any? = when (column) {
        Document.COLUMN_DOCUMENT_ID -> idFor(file)

        Document.COLUMN_DISPLAY_NAME -> file.name

        Document.COLUMN_MIME_TYPE ->
            if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"

        Document.COLUMN_SIZE -> when {
            !file.isDirectory -> file.length()
            reportFolderSize -> FOLDER_SIZE
            else -> 0L
        }

        else -> null
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        @Suppress("DEPRECATION")
        val uri = extras!!.getParcelable<Uri>(EXTRA_URI)!!
        val file = fileFor(DocumentsContract.getDocumentId(uri))
        return when (method) {
            METHOD_CREATE -> {
                val child = File(file, extras.getString(Document.COLUMN_DISPLAY_NAME)!!)
                if (extras.getString(Document.COLUMN_MIME_TYPE) == Document.MIME_TYPE_DIR) {
                    child.mkdirs()
                } else {
                    child.createNewFile()
                }
                Bundle().apply {
                    putParcelable(
                        EXTRA_URI,
                        DocumentsContract.buildDocumentUriUsingTree(uri, idFor(child))
                    )
                }
            }

            METHOD_DELETE -> Bundle().also { file.deleteRecursively() }

            else -> super.call(method, arg, extras)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
        fileFor(DocumentsContract.getDocumentId(uri)),
        ParcelFileDescriptor.parseMode(mode)
    )

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = 0

    companion object {
        const val AUTHORITY = "org.totschnig.myexpenses.test.documents"
        private const val ROOT_ID = "root"
        private const val FOLDER_SIZE = 4096L
        private const val EXTRA_URI = "uri"
        private const val METHOD_CREATE = "android:createDocument"
        private const val METHOD_DELETE = "android:deleteDocument"
    }
}
