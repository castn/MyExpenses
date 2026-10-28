package org.totschnig.ocr

import android.graphics.Rect
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedElement
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText

/** The recognized text of an engine with the positions of its lines and words */
fun Text.toRecognizedText() = RecognizedText(textBlocks.map { block ->
    RecognizedBlock(block.lines.map { line ->
        RecognizedLine(line.text, line.boundingBox?.toBox(), line.elements.map { element ->
            RecognizedElement(element.text, element.boundingBox?.toBox())
        })
    })
})

private fun Rect.toBox() = Box(left, top, right, bottom)
