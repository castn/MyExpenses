package org.totschnig.myexpenses.next.feature.attachments

import android.graphics.Bitmap
import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.data.attachments.Attachment
import org.totschnig.myexpenses.next.data.attachments.AttachmentDatabase

/** Keeps the attachments in memory and records the calls a view model makes */
private class RecordingAttachmentDatabase : AttachmentDatabase {
    val stored = HashMap<Long, MutableList<Attachment>>()
    val thumbnailsAsked = mutableListOf<Uri>()
    var failAdding = false
    var failDeleting = false
    var photos = 0

    private val changes = MutableStateFlow(0)

    override fun changesOf(transactionId: Long): Flow<Unit> = changes.map { }

    override fun load(transactionId: Long): List<Attachment> =
        stored[transactionId].orEmpty().toList()

    override fun thumbnail(uri: Uri, size: Int): Bitmap? {
        thumbnailsAsked += uri
        return null
    }

    override fun add(transactionId: Long, uri: Uri) {
        if (failAdding) throw IOException("full")
        stored.getOrPut(transactionId) { mutableListOf() } += image(uri)
        changes.value++
    }

    override fun delete(transactionId: Long, uri: Uri) {
        if (failDeleting) throw IOException("gone")
        stored[transactionId]?.removeAll { it.uri == uri }
        changes.value++
    }

    override fun newPhotoUri(): Uri = TestUri("content://app/cache/photo${++photos}.jpg")

    override fun viewableUri(uri: Uri) = uri

    override fun pickableTypes() = listOf("image/*", "application/pdf")
}

private fun image(uri: Uri) = Attachment(uri, uri.toString(), "image/webp")

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val database = RecordingAttachmentDatabase()

    private val photo = TestUri("content://app/pictures/receipt.webp")
    private val pdf = TestUri("content://documents/invoice.pdf")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        AttachmentsViewModel(savedState, database, dispatcher)

    private suspend fun AttachmentsViewModel.shownUris(transactionId: Long) =
        attachments(transactionId, 48).first().map { it.attachment.uri }

    @Test
    fun `lists the attachments of the transaction, previews only for images`() =
        runTest(dispatcher) {
            database.stored[1L] = mutableListOf(
                Attachment(photo, "receipt.webp", "image/webp"),
                Attachment(pdf, "invoice.pdf", "application/pdf")
            )
            database.stored[2L] = mutableListOf(image(TestUri("content://other")))

            val shown = viewModel().attachments(1L, 48).first()

            assertEquals(listOf("receipt.webp", "invoice.pdf"), shown.map { it.attachment.name })
            assertEquals(listOf<Uri>(photo), database.thumbnailsAsked)
        }

    @Test
    fun `adding stores the file and lists it again`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.add(1L, pdf)

        assertEquals(listOf<Uri>(pdf), viewModel.shownUris(1L))
        assertEquals(AttachmentsState(), viewModel.state.value)
    }

    @Test
    fun `a failed add is shown for its transaction until dismissed`() = runTest(dispatcher) {
        database.failAdding = true
        val viewModel = viewModel()

        viewModel.add(1L, pdf)

        assertEquals(
            AttachmentsState(failed = AttachmentFailure(1L, AttachmentFailure.Action.Add)),
            viewModel.state.value
        )
        viewModel.dismissFailure()
        assertNull(viewModel.state.value.failed)
    }

    @Test
    fun `deleting removes only the chosen attachment`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.add(1L, photo)
        viewModel.add(1L, pdf)

        viewModel.delete(1L, image(photo))

        assertEquals(listOf<Uri>(pdf), viewModel.shownUris(1L))
    }

    @Test
    fun `a failed delete is shown`() = runTest(dispatcher) {
        database.failDeleting = true
        val viewModel = viewModel()

        viewModel.delete(1L, image(photo))

        assertEquals(
            AttachmentFailure(1L, AttachmentFailure.Action.Delete),
            viewModel.state.value.failed
        )
    }

    @Test
    fun `a photo taken is attached to the transaction it was taken for`() = runTest(dispatcher) {
        val viewModel = viewModel()

        val target = viewModel.newPhoto(7L)
        viewModel.photoTaken(true)

        assertEquals(listOf(target), database.stored[7L]!!.map { it.uri })
    }

    @Test
    fun `the photo survives the end of the app while the camera is open`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val target = viewModel(savedState).newPhoto(7L)
        val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })

        viewModel(restored).photoTaken(true)

        assertEquals(listOf(target), database.stored[7L]!!.map { it.uri })
    }

    @Test
    fun `a cancelled photo is not attached, nor later`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.newPhoto(7L)
        viewModel.photoTaken(false)
        viewModel.photoTaken(true)

        assertTrue(database.stored.isEmpty())
    }

    @Test
    fun `offers the file types of the settings`() {
        assertEquals(listOf("image/*", "application/pdf"), viewModel().pickableTypes().toList())
    }
}
