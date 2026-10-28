package org.totschnig.myexpenses.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One row of a [ReceiptTable]
 *
 * @param details second line below the name, e.g. quantity and unit price
 */
@Immutable
data class ReceiptLine(val name: String, val amount: String, val details: String? = null)

/** Digits of the same width, so that amounts line up like on a printed receipt */
private val TextStyle.tabular: TextStyle
    get() = copy(fontFeatureSettings = "tnum")

/**
 * Items with their prices laid out like a clean till receipt: the shop on top, a column for the
 * items and one for the prices, a dashed line and the total. Meant as content of a [DetailCard].
 *
 * @param collapsedLines long receipts show only this many lines at first
 * @param showAllLabel button below the shortened lines that shows all of them
 */
@Composable
fun ReceiptTable(
    title: String?,
    itemHeader: String,
    amountHeader: String,
    lines: List<ReceiptLine>,
    totalLabel: String,
    total: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = Int.MAX_VALUE,
    showAllLabel: String = "",
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val isShortened = !showAll && lines.size > collapsedLines
    Column(modifier.padding(Spacing.content)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
        Row {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(itemHeader, style = style, color = color, modifier = Modifier.weight(1f))
            Text(amountHeader, style = style, color = color)
        }
        DashedLine(Modifier.padding(vertical = 8.dp))
        (if (isShortened) lines.take(collapsedLines) else lines).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        line.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    line.details?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    line.amount,
                    style = MaterialTheme.typography.bodyLarge.tabular,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.content)
                )
            }
        }
        if (isShortened) {
            Text(
                showAllLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAll = true }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight()
            )
        }
        DashedLine(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                totalLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(total, style = MaterialTheme.typography.titleMedium.tabular, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashedLine(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
    }
}
