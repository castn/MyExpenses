package org.totschnig.myexpenses.activity

import android.content.Context
import android.content.Intent

/**
 * The screens of the templates. They live in :ui_next, which :myExpenses does not know, so :app
 * hands them in through [org.totschnig.myexpenses.di.FeatureModule], e.g. for the template widget
 * and the pinned shortcuts of templates.
 */
interface TemplateScreens {
    /** The list of the templates */
    fun list(context: Context): Intent

    /** The form of a new transaction filled with template [templateId] */
    fun newTransaction(context: Context, templateId: Long): Intent
}
