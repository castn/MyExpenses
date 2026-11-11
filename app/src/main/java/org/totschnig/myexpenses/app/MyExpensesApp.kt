package org.totschnig.myexpenses.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.di.AppComponent
import org.totschnig.myexpenses.di.DaggerAppComponent
import org.totschnig.myexpenses.next.NextData

/**
 * The application class of the installed app. It only exists so that :app, which knows the
 * feature modules, can hand their implementations to the component built in :myExpenses.
 */
class MyExpensesApp : MyApplication() {
    override fun buildAppComponent(): AppComponent = DaggerAppComponent.builder()
        .applicationContext(this)
        .featureModule(AppFeatureModule())
        .build()

    override fun onCreate() {
        super.onCreate()
        // Right away, so that the new UI hears about imports that run before it is shown. Off the
        // main thread, since reading the home currency may query the database.
        if (!isSyncService) {
            MainScope().launch(Dispatchers.Default) { NextData.of(this@MyExpensesApp) }
        }
    }
}
