package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ItemGroupModelReleaseTest {

    private fun release(market: String, version: String, format: Int = 2) =
        """{"market": "$market", "version": "$version", "format": $format, "url": "https://example.org/$version.bin", "sha256": "AB12", "size": 1929216}"""

    @Test
    fun parsesManifestAndSkipsIncompleteEntries() {
        val releases = ItemGroupModelRelease.parseManifest(
            """{"models": [${release(
                "de-DE",
                "2026.10-de-1"
            )}, {"market": "fr-FR"}], "schema": 1}"""
        )
        assertEquals(
            listOf(
                ItemGroupModelRelease(
                    "de",
                    "2026.10-de-1",
                    2,
                    "https://example.org/2026.10-de-1.bin",
                    "ab12",
                    1929216
                )
            ),
            releases
        )
        assertEquals(emptyList<ItemGroupModelRelease>(), ItemGroupModelRelease.parseManifest("{}"))
    }

    @Test
    fun choosesLanguageOfDeviceElseGermanInReadableFormat() {
        val releases = ItemGroupModelRelease.parseManifest(
            """{"models": [${release("de-DE", "de-1")}, ${release("de-DE", "de-2")}, """ +
                """${release("fr-FR", "fr-1")}, ${release("it-IT", "it-9", format = 3)}]}"""
        )
        assertEquals("fr-1", ItemGroupModelRelease.choose(releases, "fr")?.version)
        // the last entry is the latest
        assertEquals("de-2", ItemGroupModelRelease.choose(releases, "de")?.version)
        assertEquals("de-2", ItemGroupModelRelease.choose(releases, "en")?.version)
        // a newer format needs a newer app
        assertEquals("de-2", ItemGroupModelRelease.choose(releases, "it")?.version)
        assertNull(ItemGroupModelRelease.choose(releases.filter { it.language == "it" }, "it"))
    }

    @Test
    fun acceptsModelOfKnownGroups() {
        val model = checkItemGroupModel(TestModels.constantFile(0.95f, "drinks", "dairy"))
        assertEquals(listOf("drinks", "dairy"), model.groups)
    }

    @Test
    fun rejectsDamagedFile() {
        val bytes = TestModels.constantFile(0.95f, "drinks", "dairy")
        bytes[100] = (bytes[100].toInt() xor 1).toByte()
        assertRejected(InvalidItemGroupModel.Reason.Invalid, bytes)
        assertRejected(InvalidItemGroupModel.Reason.Invalid, "not a model".toByteArray())
    }

    @Test
    fun rejectsModelOfUnknownGroups() {
        assertRejected(
            InvalidItemGroupModel.Reason.Incompatible,
            TestModels.constantFile(0.95f, "drinks", "future_group")
        )
    }

    @Test
    fun sha256Hex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".toByteArray())
        )
    }

    private fun assertRejected(reason: InvalidItemGroupModel.Reason, bytes: ByteArray) {
        try {
            checkItemGroupModel(bytes)
            fail("Accepted")
        } catch (e: InvalidItemGroupModel) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun parsesPublishedManifest() {
        val releases = ItemGroupModelRelease.parseManifest(
            """
            {
              "schema": 1,
              "models": [
                {
                  "format": 2,
                  "market": "de-DE",
                  "version": "de-DE-2026.10.1",
                  "url": "https://github.com/castn/product-group-modell/releases/download/model-de-DE-2026.10.1/pgm-f2-de-DE-2026.10.1.bin",
                  "size": 1841016,
                  "sha256": "225b6b0186b0d69ac7a85cc00d3665617e332164b684b8b9ab44047883862257",
                  "commit": "1bd2f482e8a608cc14c0f6a4b5a70708e5524ab3"
                }
              ]
            }
            """
        )
        assertEquals("de-DE-2026.10.1", ItemGroupModelRelease.choose(releases, "de")?.version)
    }
}
