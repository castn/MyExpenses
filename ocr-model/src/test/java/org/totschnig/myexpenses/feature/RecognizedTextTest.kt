package org.totschnig.myexpenses.feature

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecognizedTextTest {

    @Test
    fun jsonRoundTrip() {
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        RecognizedLine(
                            "BANANE 1,29 A",
                            Box(10, 100, 300, 130),
                            listOf(
                                RecognizedElement("BANANE", Box(10, 100, 120, 130)),
                                RecognizedElement("1,29", Box(200, 100, 260, 130)),
                                RecognizedElement("A", null)
                            )
                        )
                    )
                ),
                RecognizedBlock(listOf(RecognizedLine("SUMME", null, emptyList())))
            )
        )
        assertThat(recognizedTextFromJson(text.toJson())).isEqualTo(text)
        assertThat(text.lines.map { it.text }).containsExactly("BANANE 1,29 A", "SUMME").inOrder()
    }
}
