package org.totschnig.myexpenses.next

import androidx.compose.runtime.Composable
import org.totschnig.myexpenses.compose.AppTheme

/**
 * Theme of the new UI. For now identical to [AppTheme]; the new design system
 * (colors, typography, shapes) goes here.
 */
@Composable
fun NextTheme(content: @Composable () -> Unit) {
    AppTheme(content = content)
}
