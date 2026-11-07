package org.totschnig.myexpenses.provider

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.ZipUtils

@RunWith(AndroidJUnit4::class)
class BackupCharacterizationTest : BaseTestWithRepository() {

    private lateinit var backupDir: File

    @Before
    fun setUp() {
        backupDir = File(application.filesDir, "backup-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        backupDir.deleteRecursively()
        prefHandler.remove(PrefKey.PURGE_BACKUP)
        prefHandler.remove(PrefKey.PURGE_BACKUP_KEEP)
        prefHandler.remove(PrefKey.BACKUP_FILE_PREFIX)
    }

    // --- listOldBackups ---

    private fun appDir() = DocumentFile.fromFile(backupDir)

    private fun createBackupFile(
        name: String,
        lastModified: Long = System.currentTimeMillis()
    ): DocumentFile {
        val file = File(backupDir, name).apply {
            createNewFile()
            setLastModified(lastModified)
        }
        return DocumentFile.fromFile(file)
    }

    @Test
    fun listOldBackups_emptyWhenPurgeDisabled() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, false)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 1)
        createBackupFile("myexpenses-backup-20260101-120000.zip", 1000L)
        createBackupFile("myexpenses-backup-20260102-120000.zip", 2000L)

        assertThat(listOldBackups(appDir(), prefHandler)).isEmpty()
    }

    @Test
    fun listOldBackups_emptyWhenKeepIsZero() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, true)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 0)
        createBackupFile("myexpenses-backup-20260101-120000.zip", 1000L)

        assertThat(listOldBackups(appDir(), prefHandler)).isEmpty()
    }

    @Test
    fun listOldBackups_returnsOldestWhenKeepIsOne() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, true)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 1)
        createBackupFile("myexpenses-backup-20260101-120000.zip", 1000L)
        createBackupFile("myexpenses-backup-20260102-120000.zip", 2000L)
        createBackupFile("myexpenses-backup-20260103-120000.zip", 3000L)

        val old = listOldBackups(appDir(), prefHandler)

        assertThat(old.map { it.name }).containsExactly(
            "myexpenses-backup-20260101-120000.zip",
            "myexpenses-backup-20260102-120000.zip"
        )
    }

    @Test
    fun listOldBackups_ignoresFilesNotMatchingPattern() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, true)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 1)
        createBackupFile("myexpenses-backup-20260101-120000.zip", 1000L)
        createBackupFile("myexpenses-backup-20260102-120000.zip", 2000L)
        createBackupFile("some-other-file.zip", 500L)
        createBackupFile("myexpenses-backup-latest.zip", 100L)

        val old = listOldBackups(appDir(), prefHandler)

        assertThat(old.map { it.name }).containsExactly("myexpenses-backup-20260101-120000.zip")
    }

    @Test
    fun listOldBackups_usesCustomPrefix() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, true)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 1)
        prefHandler.putString(PrefKey.BACKUP_FILE_PREFIX, "my-backup")
        createBackupFile("my-backup-20260101-120000.zip", 1000L)
        createBackupFile("my-backup-20260102-120000.zip", 2000L)
        createBackupFile("myexpenses-backup-20260101-120000.zip", 500L)

        val old = listOldBackups(appDir(), prefHandler)

        assertThat(old.map { it.name }).containsExactly("my-backup-20260101-120000.zip")
    }

    @Test
    fun listOldBackups_worksWithEncryptedFiles() {
        prefHandler.putBoolean(PrefKey.PURGE_BACKUP, true)
        prefHandler.putInt(PrefKey.PURGE_BACKUP_KEEP, 1)
        createBackupFile("myexpenses-backup-20260101-120000.enc", 1000L)
        createBackupFile("myexpenses-backup-20260102-120000.enc", 2000L)

        val old = listOldBackups(appDir(), prefHandler)

        assertThat(old.map { it.name }).containsExactly("myexpenses-backup-20260101-120000.enc")
    }

    // --- ZipUtils.zipBackup ---

    private fun zipEntryNames(zipFile: File): Set<String> =
        ZipInputStream(zipFile.inputStream()).use { zip ->
            buildSet {
                var entry = zip.nextEntry
                while (entry != null) {
                    add(entry.name)
                    entry = zip.nextEntry
                }
            }
        }

    @Test
    fun zipBackup_alwaysContainsDatabaseAndPreferencesEntries() {
        val cacheDir = File(application.cacheDir, "zip-test").apply { mkdirs() }
        try {
            File(cacheDir, BACKUP_DB_FILE_NAME).writeText("fake-db")
            File(cacheDir, BACKUP_PREF_FILE_NAME).writeText("fake-prefs")

            val destFile = File(backupDir, "test.zip")
            val destDoc = DocumentFile.fromFile(destFile)

            ZipUtils.zipBackup(application, cacheDir, destDoc, null, true)

            val entries = zipEntryNames(destFile)
            assertThat(entries).contains(BACKUP_DB_FILE_NAME)
            assertThat(entries).contains(BACKUP_PREF_FILE_NAME)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun zipBackup_includesDataStoreWhenPresent() {
        val cacheDir = File(application.cacheDir, "zip-test-ds").apply { mkdirs() }
        try {
            File(cacheDir, BACKUP_DB_FILE_NAME).writeText("fake-db")
            File(cacheDir, BACKUP_PREF_FILE_NAME).writeText("fake-prefs")
            File(cacheDir, BACKUP_DATASTORE_FILE_NAME).writeText("fake-datastore")

            val destFile = File(backupDir, "test-ds.zip")
            val destDoc = DocumentFile.fromFile(destFile)

            ZipUtils.zipBackup(application, cacheDir, destDoc, null, true)

            val entries = zipEntryNames(destFile)
            assertThat(entries).contains(BACKUP_DATASTORE_FILE_NAME)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun zipBackup_omitsDataStoreWhenAbsent() {
        val cacheDir = File(application.cacheDir, "zip-test-nods").apply { mkdirs() }
        try {
            File(cacheDir, BACKUP_DB_FILE_NAME).writeText("fake-db")
            File(cacheDir, BACKUP_PREF_FILE_NAME).writeText("fake-prefs")

            val destFile = File(backupDir, "test-nods.zip")
            val destDoc = DocumentFile.fromFile(destFile)

            ZipUtils.zipBackup(application, cacheDir, destDoc, null, true)

            val entries = zipEntryNames(destFile)
            assertThat(entries).doesNotContain(BACKUP_DATASTORE_FILE_NAME)
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
