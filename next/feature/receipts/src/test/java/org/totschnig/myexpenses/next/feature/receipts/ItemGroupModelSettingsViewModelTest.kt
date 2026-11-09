package org.totschnig.myexpenses.next.feature.receipts

import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.itemgroupmodel.ProductGroupModel
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.data.receipts.InvalidItemGroupModel
import org.totschnig.myexpenses.next.data.receipts.InvalidItemGroupModel.Reason
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelRelease
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModels

@OptIn(ExperimentalCoroutinesApi::class)
class ItemGroupModelSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun release(version: String) = ItemGroupModelRelease("de", version, 1, "https://example.com/m.bin", "", 1)

    private class FakeModels(
        var installed: String? = null,
        private val release: ItemGroupModelRelease? = null,
        private val online: Boolean = true,
    ) : ItemGroupModels {
        var downloads = 0

        override fun model(): ProductGroupModel? = null
        override fun version() = installed
        override fun install(uri: Uri) = throw IOException("Not readable")
        override fun latestRelease() = if (online) release else throw IOException("No connection")
        override fun download(release: ItemGroupModelRelease) {
            downloads++
            installed = release.version
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(
        models: ItemGroupModels,
        block: suspend (ItemGroupModelSettingsViewModel) -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val viewModel = ItemGroupModelSettingsViewModel(
                models,
                ItemGroupModelSetupStore(dataStore),
                dispatcher
            )
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun showsInstalledVersion() = withViewModel(FakeModels(installed = "3")) { viewModel ->
        assertEquals(ModelStatus("3", null), viewModel.status.first())
    }

    @Test
    fun showsThatThereIsNoModel() = withViewModel(FakeModels()) { viewModel ->
        assertEquals(ModelStatus(null, null), viewModel.status.first())
    }

    @Test
    fun sameVersionIsUpToDate() {
        val models = FakeModels(installed = "3", release = release("3"))
        withViewModel(models) { viewModel ->
            viewModel.installer.checkForUpdate()
            assertEquals(ModelSetup.UpToDate, viewModel.installer.setup.value)
            assertEquals(0, models.downloads)
        }
    }

    @Test
    fun updateIsOnlyDownloadedWhenAskedFor() {
        val models = FakeModels(installed = "3", release = release("4"))
        withViewModel(models) { viewModel ->
            viewModel.installer.checkForUpdate()
            assertEquals(ModelSetup.UpdateAvailable("4"), viewModel.installer.setup.value)
            assertEquals(0, models.downloads)

            viewModel.installer.download()
            // Written by the data store in its own scope
            viewModel.installer.setup.first { it == ModelSetup.Done }
            assertEquals(ModelStatus("4", ItemGroupModelSetupStore.Source.DOWNLOAD), viewModel.status.first())
        }
    }

    @Test
    fun noReleaseIsReported() = withViewModel(FakeModels(installed = "3")) { viewModel ->
        viewModel.installer.checkForUpdate()
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.NoRelease), viewModel.installer.setup.value)
    }

    @Test
    fun missingConnectionIsReported() = withViewModel(FakeModels(online = false)) { viewModel ->
        viewModel.installer.checkForUpdate()
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.Network), viewModel.installer.setup.value)
    }

    /** Installs a file as the version its uri names, some names fail */
    private class FileModels : ItemGroupModels {
        var installed: String? = null

        override fun model(): ProductGroupModel? = null

        override fun version() = installed

        override fun install(uri: Uri) {
            installed = when (val name = uri.toString()) {
                "newer" -> throw InvalidItemGroupModel(Reason.Incompatible, "Unknown groups")
                "damaged" -> throw InvalidItemGroupModel(Reason.Invalid, "No model")
                "crash" -> error("Unexpected")
                else -> name
            }
        }

        override fun latestRelease() =
            ItemGroupModelRelease("de", "4", 1, "https://example.com/m.bin", "", 1)

        override fun download(release: ItemGroupModelRelease) = throw IOException("No connection")
    }

    @Test
    fun installedFileIsShownWithItsSource() = withViewModel(FileModels()) { viewModel ->
        viewModel.installer.install(TestUri("5"))
        // Written by the data store in its own scope
        assertEquals(ModelSetup.Done, viewModel.installer.setup.first { it != ModelSetup.Working })
        assertEquals(
            ModelStatus("5", ItemGroupModelSetupStore.Source.FILE),
            viewModel.status.first { it?.source != null }
        )
        viewModel.installer.dismiss()
        assertNull(viewModel.installer.setup.value)
    }

    @Test
    fun unreadableFileIsReported() = withViewModel(FakeModels()) { viewModel ->
        viewModel.installer.install(TestUri("unreadable"))
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.Invalid), viewModel.installer.setup.value)
        assertEquals(ModelStatus(null, null), viewModel.status.first())
    }

    @Test
    fun fileThatIsNoModelIsReported() = withViewModel(FileModels()) { viewModel ->
        listOf("damaged", "crash").forEach { name ->
            viewModel.installer.install(TestUri(name))
            val setup = viewModel.installer.setup.value
            assertEquals(ModelSetup.Failed(ModelSetup.Reason.Invalid), setup)
        }
    }

    @Test
    fun modelOfNewerAppIsReported() = withViewModel(FileModels()) { viewModel ->
        viewModel.installer.install(TestUri("newer"))
        assertEquals(
            ModelSetup.Failed(ModelSetup.Reason.Incompatible),
            viewModel.installer.setup.value
        )
    }

    @Test
    fun releaseIsOfferedWithoutInstalledModel() = withViewModel(FileModels()) { viewModel ->
        viewModel.installer.checkForUpdate()
        assertEquals(ModelSetup.UpdateAvailable("4"), viewModel.installer.setup.value)
    }

    @Test
    fun noModelIsOfferedWithoutRelease() = withViewModel(FakeModels()) { viewModel ->
        viewModel.installer.download()
        val setup = viewModel.installer.setup.value
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.NoRelease), setup)
    }
}
