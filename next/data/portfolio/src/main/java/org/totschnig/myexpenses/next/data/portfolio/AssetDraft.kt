package org.totschnig.myexpenses.next.data.portfolio

import java.io.Serializable

/**
 * A new asset as typed, e.g. a share not known to the app yet
 *
 * @param code e.g. the ticker or ISIN, stored in capitals
 * @param digitsText the decimals of its quantities
 */
data class AssetDraft(
    val code: String,
    val label: String,
    val digitsText: String,
    val kind: AssetKind
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Most shares are traded whole or with two decimals, crypto currencies with up to eight */
fun newAssetDraft(kind: AssetKind = AssetKind.SECURITY) =
    AssetDraft("", "", if (kind == AssetKind.CRYPTO) "8" else "2", kind)

/** The draft of [kind], with its usual decimals unless they were changed */
fun AssetDraft.withKind(kind: AssetKind): AssetDraft {
    val isUsual = digitsText == newAssetDraft(this.kind).digitsText
    val digits = if (isUsual) newAssetDraft(kind).digitsText else digitsText
    return copy(kind = kind, digitsText = digits)
}

const val MAX_ASSET_DIGITS = 8

/** The asset of this draft, null while code or name are empty or the decimals are too many */
fun AssetDraft.toAsset(): Asset? {
    val code = code.trim().uppercase().takeIf { it.isNotEmpty() } ?: return null
    val label = label.trim().takeIf { it.isNotEmpty() } ?: return null
    val digits =
        digitsText.trim().toIntOrNull()?.takeIf { it in 0..MAX_ASSET_DIGITS } ?: return null
    return Asset(code, label, digits, kind)
}
