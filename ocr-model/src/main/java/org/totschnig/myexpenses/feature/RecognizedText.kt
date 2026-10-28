package org.totschnig.myexpenses.feature

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raw result of text recognition with positions, independent of the engine.
 * Plain values instead of [android.graphics.Rect], so that parsers can be tested on the JVM.
 */
@Serializable
data class RecognizedText(val blocks: List<RecognizedBlock>) {
    val lines: List<RecognizedLine>
        get() = blocks.flatMap { it.lines }
}

@Serializable
data class RecognizedBlock(val lines: List<RecognizedLine>)

@Serializable
data class RecognizedLine(
    val text: String,
    val box: Box?,
    val elements: List<RecognizedElement>
)

@Serializable
data class RecognizedElement(val text: String, val box: Box?)

@Serializable
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

private val json = Json { prettyPrint = true }

fun RecognizedText.toJson() = json.encodeToString(this)

fun recognizedTextFromJson(text: String) = json.decodeFromString<RecognizedText>(text)
