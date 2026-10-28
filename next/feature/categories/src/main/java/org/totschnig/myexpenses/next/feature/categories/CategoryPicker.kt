package org.totschnig.myexpenses.next.feature.categories

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The category selection of the app. Its input is whether the category is for an income, its result
 * the id and path of the category chosen, null if the user chose none.
 */
typealias CategoryPicker = ActivityResultContract<Boolean, Pair<Long, String>?>

/** Where the app provides none, e.g. in previews: picks nothing */
private object NoCategoryPicker : CategoryPicker() {
    override fun createIntent(context: Context, input: Boolean) = Intent()
    override fun parseResult(resultCode: Int, intent: Intent?): Pair<Long, String>? = null
}

/** The category selection of the app, see [CategoryPicker] */
val LocalCategoryPicker = staticCompositionLocalOf<CategoryPicker> { NoCategoryPicker }
