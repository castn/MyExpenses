package org.totschnig.myexpenses.feature

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductKeyTest {

    @Test
    fun ignoresCaseAndPunctuation() {
        assertThat(productKey("HAFERFL.  KERNIG")).isEqualTo("haferfl kernig")
        assertThat(productKey("G&G Marmorkuchen")).isEqualTo("g g marmorkuchen")
        assertThat(productKey("Körner-Krusti")).isEqualTo("körner krusti")
    }
}
