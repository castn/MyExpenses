package org.totschnig.myexpenses.next.feature.portfolio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.FormDialog
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.AssetKind
import org.totschnig.myexpenses.next.data.portfolio.newAssetDraft
import org.totschnig.myexpenses.next.data.portfolio.toAsset
import org.totschnig.myexpenses.next.data.portfolio.withKind

/** Asks for a security or crypto currency the app does not know yet */
@Composable
internal fun AssetDialog(onConfirm: (Asset) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(newAssetDraft()) }
    val asset = draft.toAsset()
    FormDialog(
        title = stringResource(R.string.next_asset_new_title),
        onConfirm = { asset?.let(onConfirm) },
        onDismiss = onDismiss,
        confirmEnabled = asset != null,
        confirmLabel = stringResource(R.string.next_asset_add)
    ) {
        ChoiceChips(
            options = AssetKind.entries,
            selected = draft.kind,
            label = { stringResource(it.label) },
            onSelect = { draft = draft.withKind(it) }
        )
        FormTextField(
            label = stringResource(R.string.next_asset_code),
            value = draft.code,
            onValueChange = { draft = draft.copy(code = it) }
        )
        FormTextField(
            label = stringResource(R.string.next_asset_label),
            value = draft.label,
            onValueChange = { draft = draft.copy(label = it) }
        )
        FormTextField(
            label = stringResource(R.string.next_asset_digits),
            value = draft.digitsText,
            onValueChange = { draft = draft.copy(digitsText = it) },
            keyboardType = KeyboardType.Number
        )
    }
}
