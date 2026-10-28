package org.totschnig.myexpenses.feature

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpectedReceiptTest {

    private val receipt = ExpectedReceipt(
        merchant = "Aldi Süd",
        items = listOf(
            ExpectedItem("BBQ Grillkäse", 179, group = "dairy"),
            ExpectedItem("Körner-Krusti", 178, quantity = "2", unitPrice = 89, group = "bakery"),
            ExpectedItem("Pfand", 25)
        ),
        total = 382,
        itemCount = 4
    )

    @Test
    fun jsonRoundTrip() {
        assertThat(expectedReceiptFromJson(receipt.toJson())).isEqualTo(receipt)
    }

    @Test
    fun nullsAreLeftOut() {
        assertThat(receipt.toJson()).doesNotContain("null")
    }

    @Test
    fun itemGroupRows() {
        assertThat(receipt.itemGroupRows("aldi")).containsExactly(
            "aldi;BBQ Grillkäse;dairy",
            "aldi;Körner-Krusti;bakery"
        ).inOrder()
    }
}
