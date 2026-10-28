package org.totschnig.myexpenses.feature

/**
 * Lower case words of the name, so that the same product can be found on other receipts. Stored
 * with the items by the app and looked up by the classifier of the new UI, so both use this one.
 */
fun productKey(name: String) = name.lowercase()
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.isNotEmpty() }
    .joinToString(" ")
