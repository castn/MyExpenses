package org.totschnig.myexpenses.next.feature.receipts

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.itemgroupmodel.ProductGroupModel
import org.totschnig.myexpenses.next.core.common.withTestDataStore
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
        models: FakeModels,
        block: suspend (ItemGroupModelSettingsViewModel) -> Unit,
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            block(ItemGroupModelSettingsViewModel(models, ItemGroupModelSetupStore(dataStore), dispatcher))
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
}
