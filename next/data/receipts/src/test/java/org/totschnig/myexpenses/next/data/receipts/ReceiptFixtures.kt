package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Receipts of real purchases to test the parser with, kept outside of the repository: `<name>.json`
 * as recognized, `<name>.expected.json` with what a receipt shows, `item-groups.csv` with the groups
 * of their items and `parser-snapshot.txt`. Tests that need them run with
 * `-PreceiptFixtures=<directory>` and are skipped without.
 */
object ReceiptFixtures {
    private val directory: File? = System.getProperty("receiptFixtures")?.let(::File)?.takeIf { it.isDirectory }

    /** The directory of the fixtures; skips the test without it */
    fun dir(): File {
        assumeTrue("No receipt fixtures, see -PreceiptFixtures in build.gradle", directory != null)
        return directory!!
    }

    /** The recognized text of the receipt [name] */
    fun text(name: String) = File(dir(), "$name.json").readText()
}
