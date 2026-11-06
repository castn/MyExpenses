package org.totschnig.myexpenses.provider

import android.content.Context

const val FIRST_OWN_DATABASE_VERSION = 1000

/** The last version of upstream that this fork knows */
private const val LAST_KNOWN_UPSTREAM_VERSION = 191

/** The highest version this fork used before it got its own numbers, always with receipts */
private const val LAST_OLD_OWN_VERSION = 193

/**
 * open a database file and return its version
 */
fun interface DatabaseVersionPeekHelper {
    fun checkVersion(context: Context, path: String): Result<Unit>
}

/** Whether a database comes from a version of upstream that is newer than this fork knows */
fun isFromNewerUpstream(version: Int, hasReceipts: Boolean) = when {
    version <= LAST_KNOWN_UPSTREAM_VERSION || version >= FIRST_OWN_DATABASE_VERSION -> false
    version <= LAST_OLD_OWN_VERSION -> !hasReceipts
    else -> true
}
