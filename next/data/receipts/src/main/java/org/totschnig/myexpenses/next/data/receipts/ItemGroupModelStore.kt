package org.totschnig.myexpenses.next.data.receipts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.totschnig.itemgroupmodel.ProductGroupModel
import timber.log.Timber

/**
 * The model for item groups on the device, see [ItemGroupModelStore]. All functions block, call
 * them off the main thread.
 */
interface ItemGroupModels {
    /** null if there is none or it cannot be read any more, e.g. after an update of the app */
    fun model(): ProductGroupModel?

    /** Version of [model] */
    fun version(): String? = model()?.version

    /** A model file the user picked, see [ItemGroupModelStore.install] */
    fun install(uri: Uri)

    /** null if no model is offered for this app */
    fun latestRelease(): ItemGroupModelRelease?

    fun download(release: ItemGroupModelRelease)
}

/**
 * The model for item groups on the device: downloaded or picked by the user, kept in the files
 * of the app and loaded once. All functions block, call them off the main thread.
 */
class ItemGroupModelStore private constructor(
    private val dir: File,
    private val contentResolver: ContentResolver
) : ItemGroupModels {

    private val file = File(dir, "model.bin")
    private var model: ProductGroupModel? = null
    private var loaded = false

    /** null if there is none or it cannot be read any more, e.g. after an update of the app */
    @Synchronized
    override fun model(): ProductGroupModel? {
        if (!loaded) {
            model = if (file.exists()) {
                try {
                    checkItemGroupModel(file.readBytes())
                } catch (e: Exception) {
                    Timber.w(e, "Item group model not usable")
                    null
                }
            } else {
                null
            }
            loaded = true
        }
        return model
    }

    /**
     * Replaces the model only once the new one is checked
     *
     * @throws InvalidItemGroupModel
     */
    @Synchronized
    fun install(bytes: ByteArray): ProductGroupModel {
        val checked = checkItemGroupModel(bytes)
        dir.mkdirs()
        val temp = File(dir, "model.bin.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not store model")
        }
        model = checked
        loaded = true
        return checked
    }

    /** @throws InvalidItemGroupModel, IOException */
    override fun install(uri: Uri) {
        install(
            contentResolver.openInputStream(uri)?.use { it.readLimited() }
                ?: throw IOException("Cannot open $uri")
        )
    }

    /**
     * null if no model is offered for this app, also while there is no manifest yet
     *
     * @throws IOException
     */
    override fun latestRelease(): ItemGroupModelRelease? {
        val manifest = try {
            get(ItemGroupModelRelease.MANIFEST_URL).decodeToString()
        } catch (_: FileNotFoundException) {
            return null
        }
        return ItemGroupModelRelease.choose(
            ItemGroupModelRelease.parseManifest(manifest),
            Locale.getDefault().language
        )
    }

    /** @throws InvalidItemGroupModel, IOException */
    override fun download(release: ItemGroupModelRelease) {
        val bytes = get(release.url)
        if (bytes.size.toLong() != release.size || sha256(bytes) != release.sha256) {
            throw IOException("Download of ${release.version} incomplete or changed")
        }
        install(bytes)
    }

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            if (connection.responseCode ==
                HttpURLConnection.HTTP_NOT_FOUND
            ) {
                throw FileNotFoundException(url)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.use { it.readLimited() }
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readLimited(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > ItemGroupModelRelease.MAX_SIZE) throw IOException("File too large")
        }
    }

    companion object {
        private const val TIMEOUT = 30_000

        @Volatile
        private var instance: ItemGroupModelStore? = null

        fun of(context: Context): ItemGroupModelStore = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                ItemGroupModelStore(File(app.filesDir, "item_groups"), app.contentResolver)
            }.also { instance = it }
        }
    }
}
