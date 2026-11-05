package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ItemGroupModelReleaseTest {

    private fun release(language: String, version: String, format: Int = 2) =
        """{"language": "$language", "version": "$version", "format": $format, "url": "https://example.org/$version.bin", "sha256": "AB12", "size": 1929216}"""

    @Test
    fun parsesManifestAndSkipsIncompleteEntries() {
        val releases = ItemGroupModelRelease.parseManifest(
            """{"models": [${release("de", "2026.10-de-1")}, {"language": "fr"}], "comment": "ignored"}"""
        )
        assertEquals(
            listOf(ItemGroupModelRelease("de", "2026.10-de-1", 2, "https://example.org/2026.10-de-1.bin", "ab12", 1929216)),
            releases
        )
        assertEquals(emptyList<ItemGroupModelRelease>(), ItemGroupModelRelease.parseManifest("{}"))
    }

    @Test
    fun choosesLanguageOfDeviceElseGermanInReadableFormat() {
        val releases = ItemGroupModelRelease.parseManifest(
            """{"models": [${release("de", "de-1")}, ${release("de", "de-2")}, ${release("fr", "fr-1")}, ${release("it", "it-9", format = 3)}]}"""
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
        assertRejected(InvalidItemGroupModel.Reason.Incompatible, TestModels.constantFile(0.95f, "drinks", "future_group"))
    }

    @Test
    fun sha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".toByteArray()))
    }

    private fun assertRejected(reason: InvalidItemGroupModel.Reason, bytes: ByteArray) {
        try {
            checkItemGroupModel(bytes)
            fail("Accepted")
        } catch (e: InvalidItemGroupModel) {
            assertEquals(reason, e.reason)
        }
    }
}
