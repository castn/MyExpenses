package org.totschnig.myexpenses.next.data.portfolio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetDraftTest {

    @Test
    fun cryptoStartsWithEightDecimals() {
        assertEquals("2", newAssetDraft().digitsText)
        assertEquals("8", newAssetDraft().withKind(AssetKind.CRYPTO).digitsText)
        assertEquals(
            "4",
            newAssetDraft().copy(digitsText = "4").withKind(AssetKind.CRYPTO).digitsText
        )
    }

    @Test
    fun assetNeedsCodeNameAndDecimalsUpToEight() {
        val draft = AssetDraft(" aapl ", " Apple ", "0", AssetKind.SECURITY)
        assertEquals(Asset("AAPL", "Apple", 0, AssetKind.SECURITY), draft.toAsset())
        assertNull(draft.copy(code = " ").toAsset())
        assertNull(draft.copy(label = "").toAsset())
        assertNull(draft.copy(digitsText = "9").toAsset())
        assertNull(draft.copy(digitsText = "x").toAsset())
    }
}
