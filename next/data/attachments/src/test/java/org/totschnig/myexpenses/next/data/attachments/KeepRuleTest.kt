package org.totschnig.myexpenses.next.data.attachments

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.data.attachments.AttachmentOrigin.External
import org.totschnig.myexpenses.next.data.attachments.AttachmentOrigin.Home
import org.totschnig.myexpenses.next.data.attachments.AttachmentOrigin.Temporary

class KeepRuleTest {

    private fun rule(
        origin: AttachmentOrigin,
        isImage: Boolean = true,
        copy: Boolean = true,
        optimize: Boolean = true
    ) = keepRuleFor(origin, isImage, copyExternal = copy, optimizePhotos = optimize)

    @Test
    fun `files already in the pictures of the app stay`() {
        val all = listOf(true, false)
        for (isImage in all) {
            for (copy in all) {
                for (optimize in all) {
                    assertEquals(KeepRule.Keep, rule(Home, isImage, copy, optimize))
                }
            }
        }
    }

    @Test
    fun `files of other apps are only linked without copies`() {
        assertEquals(KeepRule.Keep, rule(External, isImage = true, copy = false))
        assertEquals(KeepRule.Keep, rule(External, isImage = false, copy = false))
    }

    @Test
    fun `photos are reduced when optimizing`() {
        assertEquals(KeepRule.Optimize, rule(External))
        assertEquals(KeepRule.Optimize, rule(Temporary))
    }

    @Test
    fun `a new photo is kept even without copies of other files`() {
        assertEquals(KeepRule.Optimize, rule(Temporary, copy = false))
        assertEquals(KeepRule.Copy, rule(Temporary, copy = false, optimize = false))
    }

    @Test
    fun `other files and photos without optimizing are copied unchanged`() {
        assertEquals(KeepRule.Copy, rule(External, isImage = false))
        assertEquals(KeepRule.Copy, rule(External, optimize = false))
    }
}
