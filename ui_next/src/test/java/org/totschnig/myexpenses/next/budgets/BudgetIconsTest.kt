package org.totschnig.myexpenses.next.budgets

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.data.budgets.PotTemplate
import org.totschnig.myexpenses.viewmodel.data.FontAwesomeIcons

class BudgetIconsTest {

    @Test
    fun everyIconOfATemplateExistsInFontAwesome() {
        assertEquals(emptyList<String>(), PotTemplate.entries.map { it.icon }.filter { it !in FontAwesomeIcons })
    }
}
