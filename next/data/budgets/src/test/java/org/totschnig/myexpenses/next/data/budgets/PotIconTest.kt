package org.totschnig.myexpenses.next.data.budgets

import org.junit.Assert.assertEquals
import org.junit.Test

class PotIconTest {

    @Test
    fun potShowsChosenIconElseOneOfItsName() {
        assertEquals("gift", Pot(name = "Fitness", kind = PotKind.RUNNING, icon = "gift").iconName)
        assertEquals("dumbbell", Pot(name = "Fitness", kind = PotKind.RUNNING).iconName)
        assertEquals(null, Pot(name = "Xyzzy", kind = PotKind.RUNNING).iconName)
    }
}
