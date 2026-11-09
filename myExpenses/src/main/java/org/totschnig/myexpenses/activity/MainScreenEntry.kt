package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.injector

/**
 * The activity of the main screen. It lives in :ui_next, which :myExpenses does not know, so :app
 * hands it in through [org.totschnig.myexpenses.di.FeatureModule].
 */
class MainScreenEntry(val activityClass: Class<out Activity>)

/** Opens the main screen, e.g. from the launcher, a widget, a notification or after a restore */
fun Context.mainScreenIntent() = Intent(this, injector.mainScreen().activityClass)
