package org.totschnig.fixturecollector

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

class FixtureNamesTest {

    private val takenAt = LocalDateTime.of(2026, 10, 3, 14, 32)

    @Test
    fun merchantPart() {
        assertThat(FixtureNames.merchantPart("Aldi Süd")).isEqualTo("aldi")
        assertThat(FixtureNames.merchantPart("EDEKA Kampmann")).isEqualTo("edeka")
        assertThat(FixtureNames.merchantPart("Müller")).isEqualTo("mueller")
        assertThat(FixtureNames.merchantPart("dm-drogerie markt")).isEqualTo("dm")
        assertThat(FixtureNames.merchantPart("*** REWE ***")).isEqualTo("rewe")
        assertThat(FixtureNames.merchantPart("***")).isNull()
        assertThat(FixtureNames.merchantPart(null)).isNull()
    }

    @Test
    fun suggest() {
        assertThat(
            FixtureNames.suggest("Aldi Süd", takenAt) {
                false
            }
        ).isEqualTo("aldi-20261003-1432")
        assertThat(FixtureNames.suggest(null, takenAt) { false }).isEqualTo("bon-20261003-1432")
    }

    @Test
    fun suggestSkipsTakenNames() {
        val taken = setOf("aldi-20261003-1432", "aldi-20261003-1432-2")
        assertThat(
            FixtureNames.suggest("Aldi", takenAt) {
                it in taken
            }
        ).isEqualTo("aldi-20261003-1432-3")
    }

    @Test
    fun suggestionIsValid() {
        assertThat(
            FixtureStore.isValidName(
                FixtureNames.suggest("Bäckerei Süß & Co.", takenAt) {
                    false
                }
            )
        ).isTrue()
    }
}
