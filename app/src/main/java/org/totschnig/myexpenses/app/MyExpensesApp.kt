package org.totschnig.myexpenses.app

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.di.AppComponent
import org.totschnig.myexpenses.di.DaggerAppComponent

/**
 * The application class of the installed app. It only exists so that :app, which knows the
 * feature modules, can hand their implementations to the component built in :myExpenses.
 */
class MyExpensesApp : MyApplication() {
    override fun buildAppComponent(): AppComponent = DaggerAppComponent.builder()
        .applicationContext(this)
        .featureModule(AppFeatureModule())
        .build()
}
