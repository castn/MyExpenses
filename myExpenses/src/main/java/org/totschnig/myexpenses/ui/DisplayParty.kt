package org.totschnig.myexpenses.ui

import android.database.Cursor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_SHORT_NAME
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull

@Parcelize
data class DisplayParty(val id: Long?, val name: String, val shortName: String? = null) :
    Parcelable {

    val displayName: String
        get() = shortName ?: name

    companion object {
        fun fromCursor(cursor: Cursor) = cursor.getLongOrNull(KEY_PAYEEID)?.let {
            DisplayParty(
                it,
                cursor.getString(KEY_PAYEE_NAME),
                cursor.getStringOrNull(KEY_SHORT_NAME)
            )
        }
    }
}
