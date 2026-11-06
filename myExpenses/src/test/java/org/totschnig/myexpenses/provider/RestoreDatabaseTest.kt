package org.totschnig.myexpenses.provider

import android.os.Bundle
import android.os.Looper
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.viewmodel.RestoreViewModel

@RunWith(AndroidJUnit4::class)
class RestoreDatabaseTest : TransactionProviderBaseTest() {

    private val databases get() = application.getDatabasePath("data").parentFile!!
    private val currentDb get() = File(databases, "data")
    private val tempDb get() = File(databases, "data.restoring")

    private lateinit var backup: File

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        // accountId from the base class is in the backup, what comes later is not
        backup = File.createTempFile("backup", null, application.cacheDir)
        currentDb.copyTo(backup, overwrite = true)
        insertAccount("After the backup")
    }

    @After
    fun tearDown() {
        tempDb.deleteRecursively()
        backup.deleteRecursively()
    }

    private fun accounts() = contentResolver
        .query(TransactionProvider.ACCOUNTS_URI, arrayOf(KEY_ROWID), null, null, null)!!
        .use { it.count }

    /** The temp file cannot be written if a directory with content stands in its way */
    private fun blockTempFile() {
        File(tempDb, "blocking").apply { parentFile!!.mkdirs() }.writeText("")
    }

    @Test
    fun replacesDatabaseWithBackup() {
        assertThat(accounts()).isEqualTo(2)

        assertThat(DbUtils.restore(application, backup, false)).isTrue()

        assertThat(accounts()).isEqualTo(1)
        assertThat(tempDb.exists()).isFalse()
    }

    @Test
    fun reportsFailureWhenBackupCannotBeRead() {
        backup.delete()
        backup.mkdir()

        assertThat(DbUtils.restore(application, backup, false)).isFalse()

        assertThat(accounts()).isEqualTo(2)
    }

    @Test
    fun keepsDatabaseWhenBackupCannotBeWritten() {
        blockTempFile()

        assertThat(DbUtils.restore(application, backup, false)).isFalse()

        assertThat(accounts()).isEqualTo(2)
    }

    @Test
    fun removesTempFileAfterFailure() {
        backup.delete()
        backup.mkdir()

        provider.restore(backup, false)

        assertThat(tempDb.exists()).isFalse()
    }

    @Test
    fun keepsEncryptionSettingWhenEncryptingFails() {
        // Tests have no :sqlcrypt, so that encrypting throws
        assertThat(DbUtils.restore(application, backup, true)).isFalse()

        assertThat(prefHandler.getBoolean(PrefKey.ENCRYPT_DATABASE, false)).isFalse()
        assertThat(accounts()).isEqualTo(2)
    }

    @Test
    fun keepsUiSettingsAndForgetsTempPreferencesAfterFailure() {
        val key = stringPreferencesKey("kept")
        runBlocking { dataStore.edit { it[key] = "value" } }
        val archive = File.createTempFile("archive", ".zip", application.cacheDir)
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_DB_FILE_NAME))
            zip.write(backup.readBytes())
            zip.putNextEntry(ZipEntry(BACKUP_PREF_FILE_NAME))
            zip.write(
                """<map><string name="fromBackup">value</string></map>""".toByteArray()
            )
        }
        blockTempFile()
        val viewModel = RestoreViewModel(application)
        application.appComponent.inject(viewModel)

        viewModel.startRestore(
            Bundle().apply { putParcelable(RestoreViewModel.KEY_FILE_PATH, archive.toUri()) }
        )
        val deadline = System.currentTimeMillis() + 10_000
        while (viewModel.result.value == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertThat(viewModel.result.value!!.isFailure).isTrue()
        assertThat(runBlocking { dataStore.data.first()[key] }).isEqualTo("value")
        val tempPrefs = File(application.filesDir.parentFile, "shared_prefs/backup_temp.xml")
        assertThat(tempPrefs.exists()).isFalse()
        assertThat(application.getSharedPreferences("backup_temp", 0).all).isEmpty()
        archive.delete()
    }
}
