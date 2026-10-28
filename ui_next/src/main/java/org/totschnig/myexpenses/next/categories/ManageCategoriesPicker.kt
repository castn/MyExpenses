package org.totschnig.myexpenses.next.categories

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.activity.Action
import org.totschnig.myexpenses.activity.ManageCategories
import org.totschnig.myexpenses.db2.asCategoryType
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.next.feature.categories.CategoryPicker

/** [CategoryPicker] with the category screen of the app, filtered to income or expense */
object ManageCategoriesPicker : CategoryPicker() {
    override fun createIntent(context: Context, input: Boolean) =
        Intent(context, ManageCategories::class.java).apply {
            action = Action.SELECT_MAPPING.name
            putExtra(KEY_TYPE, input.asCategoryType)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<Long, String>? {
        val data = intent?.takeIf { resultCode == Activity.RESULT_OK } ?: return null
        val categoryId = data.getLongExtra(KEY_ROWID, 0).takeIf { it != 0L } ?: return null
        return categoryId to (data.getStringExtra(KEY_LABEL) ?: "")
    }
}
