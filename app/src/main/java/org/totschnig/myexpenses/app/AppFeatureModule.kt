package org.totschnig.myexpenses.app

import org.totschnig.myexpenses.di.FeatureModule
import org.totschnig.myexpenses.di.SqlCryptProvider
import org.totschnig.sqlcrypt.SQLiteOpenHelperFactory

/**
 * Hands the implementations of the feature modules to the component of :myExpenses, which only
 * knows their interfaces.
 */
class AppFeatureModule : FeatureModule() {
    private val sqlCryptProvider by lazy { SQLiteOpenHelperFactory() }

    override fun provideSqlCryptProvider(): SqlCryptProvider = sqlCryptProvider
}
