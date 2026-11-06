package org.totschnig.fixturecollector

import com.google.common.truth.Truth.assertThat
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.ExpectedItem
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText

class FixtureStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val text =
        RecognizedText(
            listOf(RecognizedBlock(listOf(RecognizedLine("REWE", Box(0, 0, 10, 10), emptyList()))))
        )
    private val expected =
        ExpectedReceipt(
            "REWE",
            listOf(ExpectedItem("BANANE", 129, group = "produce"), ExpectedItem("TUETE", 10)),
            139
        )

    private fun photo() = folder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun saveAndLoad() {
        val store = FixtureStore(folder.newFolder("fixtures"))
        store.save("rewe", text, expected, photo())
        assertThat(store.names()).containsExactly("rewe")
        val fixture = store.load("rewe")
        assertThat(fixture.text).isEqualTo(text)
        assertThat(fixture.expected).isEqualTo(expected)
        assertThat(fixture.image.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(
            store.dir.resolve("rewe.item-groups.csv").readText()
        ).isEqualTo("rewe;BANANE;produce\n")
    }

    @Test
    fun renameMovesFiles() {
        val store = FixtureStore(folder.newFolder("fixtures"))
        store.save("rewe", text, expected, photo())
        store.save("rewe-pfand", text, expected, store.image("rewe"), previousName = "rewe")
        assertThat(store.names()).containsExactly("rewe-pfand")
        assertThat(store.dir.list()!!.toList()).containsExactly(
            "rewe-pfand.jpg",
            "rewe-pfand.json",
            "rewe-pfand.expected.json",
            "rewe-pfand.item-groups.csv"
        )
    }

    @Test
    fun zipIsFlat() {
        val store = FixtureStore(folder.newFolder("fixtures"))
        val photo = photo()
        store.save("rewe", text, expected, photo)
        store.save("aldi", text, ExpectedReceipt(), photo)
        val zip = folder.root.resolve("out/fixtures.zip")
        store.zipTo(zip) { photo, _ -> photo.readBytes() }
        ZipFile(zip).use { file ->
            assertThat(file.entries().toList().map { it.name }).containsExactly(
                "aldi.json",
                "aldi.expected.json",
                "aldi.jpg",
                "rewe.json",
                "rewe.expected.json",
                "rewe.item-groups.csv",
                "rewe.jpg"
            )
        }
    }

    @Test
    fun zipIsAnonymized() {
        val store = FixtureStore(folder.newFolder("fixtures"))
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        RecognizedLine("EDEKA Kampmann", Box(0, 0, 300, 80), emptyList()),
                        RecognizedLine("Lindenstraße 16", Box(0, 100, 300, 180), emptyList()),
                        RecognizedLine("BANANE 1,29", Box(0, 200, 300, 280), emptyList())
                    )
                )
            )
        )
        store.save(
            "edeka",
            text,
            ExpectedReceipt("EDEKA Kampmann", listOf(ExpectedItem("BANANE", 129))),
            photo()
        )
        val zip = folder.root.resolve("out/fixtures.zip")
        var blackened: List<Box>? = null
        store.zipTo(zip) { photo, boxes ->
            blackened = boxes
            photo.readBytes()
        }
        ZipFile(zip).use { file ->
            val json = file.getInputStream(file.getEntry("edeka.json")).reader().readText()
            assertThat(json).doesNotContain("Kampmann")
            assertThat(json).doesNotContain("Lindenstraße")
            val expected = file.getInputStream(
                file.getEntry("edeka.expected.json")
            ).reader().readText()
            assertThat(expected).contains("\"merchant\": \"EDEKA\"")
        }
        assertThat(blackened).contains(Box(0, 100, 300, 180))
        // The device keeps the original for recognizing it again
        assertThat(store.load("edeka").expected.merchant).isEqualTo("EDEKA Kampmann")
    }

    @Test
    fun names() {
        assertThat(FixtureStore.isValidName("edeka-hummus")).isTrue()
        assertThat(FixtureStore.isValidName("Edeka")).isFalse()
        assertThat(FixtureStore.isValidName("edeka hummus")).isFalse()
        assertThat(FixtureStore.isValidName("item-groups")).isFalse()
    }

    @Test
    fun markedLinesStayOnDeviceAndAreHiddenInExport() {
        val store = FixtureStore(folder.newFolder("fixtures"))
        val secret = Box(0, 100, 300, 180)
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        RecognizedLine("REWE", Box(0, 0, 300, 80), emptyList()),
                        RecognizedLine("Kundin Erika", secret, emptyList()),
                        RecognizedLine("BANANE 1,29", Box(0, 200, 300, 280), emptyList())
                    )
                )
            )
        )
        store.save(
            "rewe",
            text,
            ExpectedReceipt("REWE", listOf(ExpectedItem("BANANE", 129))),
            photo(),
            marked = setOf(secret)
        )
        assertThat(store.names()).containsExactly("rewe")
        assertThat(store.load("rewe").marked).containsExactly(secret)
        val zip = folder.root.resolve("out/fixtures.zip")
        var blackened: List<Box>? = null
        store.zipTo(zip) { photo, boxes ->
            blackened = boxes
            photo.readBytes()
        }
        ZipFile(zip).use { file ->
            assertThat(file.entries().toList().map { it.name }).doesNotContain("rewe.marked.json")
            assertThat(
                file.getInputStream(file.getEntry("rewe.json")).reader().readText()
            ).doesNotContain("Erika")
        }
        assertThat(blackened).contains(secret)
    }
}
