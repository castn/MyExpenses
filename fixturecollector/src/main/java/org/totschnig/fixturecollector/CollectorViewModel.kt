package org.totschnig.fixturecollector

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.mlkitcore.TextRecognizer
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.RecognizedText

/**
 * A fixture being edited
 *
 * @param previousName name it is stored under, null for a new photo
 * @param photo the image ML Kit reads, saved byte by byte as `<name>.jpg`
 * @param text null while recognizing or after recognition failed
 * @param takenAt time of a new photo, part of the suggested name; null for a stored fixture,
 * whose name stays
 * @param nameByUser the user chose [name], so it no longer follows the merchant
 * @param marked boxes of lines the user marked to hide in the export
 */
data class Draft(
    val previousName: String?,
    val name: String,
    val photo: File,
    val text: RecognizedText?,
    val expected: ExpectedReceipt,
    val isRecognizing: Boolean = false,
    val isDirty: Boolean = false,
    val takenAt: LocalDateTime? = null,
    val nameByUser: Boolean = false,
    val marked: Set<Box> = emptySet()
)

private const val TAG = "FixtureCollector"

data class Message(@StringRes val text: Int, val arg: String? = null)

class CollectorViewModel(application: Application) : AndroidViewModel(application) {

    private val store = FixtureStore(File(application.filesDir, "fixtures"))

    /** Photos not saved yet, copied from the cache of the scanner */
    private val work = File(application.filesDir, "work").apply { mkdirs() }

    private val _fixtures = MutableStateFlow(emptyList<FixtureStore.Summary>())
    val fixtures: StateFlow<List<FixtureStore.Summary>> = _fixtures.asStateFlow()

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()

    init {
        refresh()
        // Left over from a draft that was never saved
        viewModelScope.launch(Dispatchers.IO) { work.listFiles()?.forEach { it.delete() } }
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _fixtures.value = store.summaries() }
    }

    fun showMessage(message: Message) {
        _message.value = message
    }

    fun messageShown() {
        _message.value = null
    }

    /** Photo from the scanner or the gallery, recognized and filled with a suggestion */
    fun newPhoto(uri: Uri) {
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) {
                try {
                    File(work, "${System.currentTimeMillis()}.jpg").also { file ->
                        getApplication<Application>().contentResolver.openInputStream(
                            uri
                        )!!.use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, e)
                    null
                }
            }
            if (photo == null) {
                _message.value = Message(R.string.error_photo)
                return@launch
            }
            _draft.value = Draft(
                null,
                "",
                photo,
                null,
                ExpectedReceipt(),
                isRecognizing = true,
                isDirty = true,
                takenAt = LocalDateTime.now()
            ).withSuggestedName()
            recognize(suggest = true)
        }
    }

    fun open(name: String) {
        viewModelScope.launch {
            val fixture = withContext(Dispatchers.IO) { store.load(name) }
            _draft.value =
                Draft(
                    name,
                    name,
                    fixture.image,
                    fixture.text,
                    fixture.expected,
                    marked = fixture.marked
                )
        }
    }

    /** Recognizes the photo again, e.g. with a new version of ML Kit; the expected values stay */
    fun recognizeAgain() {
        _draft.update { it?.copy(isRecognizing = true) }
        viewModelScope.launch { recognize(suggest = false) }
    }

    private suspend fun recognize(suggest: Boolean) {
        val photo = _draft.value?.photo ?: return
        val text = try {
            TextRecognizer.recognize(
                getApplication(),
                Uri.fromFile(photo),
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
        } catch (e: Exception) {
            Log.w(TAG, e)
            _message.value = Message(R.string.error_recognition, e.message)
            null
        }
        _draft.update { draft ->
            if (draft?.photo != photo) return@update draft
            draft.copy(
                text = text ?: draft.text,
                // Boxes of a new recognition may differ, marks only stay on lines found again
                marked =
                    text?.let { new ->
                        draft.marked.intersect(new.lines.mapNotNull { it.box }.toSet())
                    }
                        ?: draft.marked,
                expected = when {
                    text == null -> draft.expected
                    suggest -> Suggestion.of(text, BuildConfig.MLKIT_VERSION)
                    else -> draft.expected.copy(mlkitVersion = BuildConfig.MLKIT_VERSION)
                },
                isRecognizing = false,
                isDirty = draft.isDirty || text != null
            ).withSuggestedName()
        }
    }

    /** An empty name goes back to the suggestion, a stored fixture keeps its name */
    fun rename(name: String) {
        _draft.update {
            if (it == null || (name.isBlank() && it.takenAt == null)) {
                it
            } else {
                it.copy(
                    name = name.trim(),
                    nameByUser = name.isNotBlank(),
                    isDirty = true
                ).withSuggestedName()
            }
        }
    }

    /** Marks the line with [box] to be hidden in the export, or takes the mark back */
    fun toggleMark(box: Box) {
        _draft.update {
            it?.copy(
                marked = if (box in
                    it.marked
                ) {
                    it.marked - box
                } else {
                    it.marked + box
                },
                isDirty = true
            )
        }
    }

    fun updateExpected(transform: (ExpectedReceipt) -> ExpectedReceipt) {
        _draft.update {
            it?.copy(expected = transform(it.expected), isDirty = true)?.withSuggestedName()
        }
    }

    /** Name from merchant and time of the photo, until the user chooses one */
    private fun Draft.withSuggestedName() = if (takenAt == null || nameByUser) {
        this
    } else {
        copy(name = FixtureNames.suggest(expected.merchant, takenAt, store::exists))
    }

    fun save() {
        val draft = _draft.value ?: return
        val error = when {
            draft.text == null -> Message(R.string.error_no_text)

            !FixtureStore.isValidName(draft.name) -> Message(R.string.error_name_invalid)

            draft.name != draft.previousName && store.exists(
                draft.name
            ) -> Message(R.string.error_name_exists, draft.name)

            else -> null
        }
        if (error != null) {
            _message.value = error
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            store.save(
                draft.name,
                draft.text!!,
                draft.expected,
                draft.photo,
                draft.previousName,
                draft.marked
            )
            if (draft.photo.parentFile == work) draft.photo.delete()
            _draft.value = null
            _fixtures.value = store.summaries()
        }
    }

    fun discard() {
        val draft = _draft.value ?: return
        _draft.value = null
        if (draft.photo.parentFile == work) {
            viewModelScope.launch(Dispatchers.IO) { draft.photo.delete() }
        }
    }

    fun delete() {
        val name = _draft.value?.previousName ?: return
        _draft.value = null
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(name)
            _fixtures.value = store.summaries()
        }
    }

    /** All fixtures anonymized as ZIP in the cache, shared through the FileProvider */
    suspend fun zip(): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "shared")
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "fixtures-${LocalDate.now()}.zip").also { store.zipTo(it, ::blacken) }
    }
}
