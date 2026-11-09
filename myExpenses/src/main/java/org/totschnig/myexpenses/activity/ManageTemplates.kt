/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener
import org.totschnig.myexpenses.fragment.TemplatesList
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.ContribPayload

const val HELP_VARIANT_TEMPLATES = "templates"

class ManageTemplates : ProtectedFragmentActivity(), ConfirmationDialogListener, ContribIFace {
    private val mListFragment
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as TemplatesList


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHelpVariant(HELP_VARIANT_TEMPLATES, true)
        setupWithFragment(savedInstanceState == null) {
            TemplatesList()
        }
        setupToolbar()
        title = getString(R.string.menu_templates)
    }

    override val fabDescription = R.string.menu_create_template

    override val fabActionName = "CREATE_TEMPLATE"

    override fun onFabClicked() {
        super.onFabClicked()
        startActivity(Intent(this, ExpenseEdit::class.java).apply {
            putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
            putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
        })
    }

    override fun dispatchCommand(command: Int, tag: Any?) =
        if (super.dispatchCommand(command, tag)) {
            true
        } else when (command) {
            R.id.DELETE_COMMAND_DO -> {
                finishActionMode()
                mListFragment.dispatchDeleteDo((tag as LongArray))
                true
            }

            R.id.CANCEL_CALLBACK_COMMAND -> {
                finishActionMode()
                true
            }
            else -> false
        }

    override fun doHome() {
        if (isTaskRoot) {
            val upIntent = NavUtils.getParentActivityIntent(this)
            // create new task
            TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent!!)
                .startActivities()
        } else {
            // Stay in same task
           super.doHome()
        }
    }

    private fun finishActionMode() {
        mListFragment.finishActionMode()
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: ContribPayload?) {
        if (feature == ContribFeature.SPLIT_TRANSACTION) {
            when (tag) {
                is ContribPayload.InstantiateSplitTemplateEdit -> {
                    mListFragment.dispatchCreateInstanceEditDo(tag.templateId)
                }
                is ContribPayload.InstantiateSplitTemplateSave -> {
                    tag.rowIds?.let { mListFragment.dispatchCreateInstanceSaveDo(it) }
                }
                else -> {}
            }
        }
    }
}
