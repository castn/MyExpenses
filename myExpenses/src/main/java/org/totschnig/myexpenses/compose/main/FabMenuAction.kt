package org.totschnig.myexpenses.compose.main

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

interface FabMenuAction {
    val imageVector: ImageVector

    @get:StringRes val label: Int
    val contentDescription: String
        @Composable get
}
