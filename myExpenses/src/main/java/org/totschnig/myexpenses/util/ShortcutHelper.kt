package org.totschnig.myexpenses.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.TemplateSaver
import org.totschnig.myexpenses.db2.entities.Template.Action
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.TemplateInfo
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET_DATA_ENTRY

object ShortcutHelper {
    const val ID_TRANSACTION = "transaction"
    const val ID_TRANSFER = "transfer"
    const val ID_SPLIT = "split"
    fun idTemplate(templateId: Long) = "template-$templateId"

    /**
     * Takes away the shortcuts for new transfers and splits, which are no longer entered by hand:
     * the dynamic ones, and those pinned to the launcher are disabled
     */
    fun removeTransferAndSplitShortcuts(context: Context) {
        val ids = listOf(ID_TRANSFER, ID_SPLIT)
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
            ShortcutManagerCompat.disableShortcuts(context, ids, null)
        } catch (e: Exception) {
            CrashHandler.report(e)
        }
    }

    fun buildTemplateIntent(context: Context, id: Long, templateAction: Action) =
        if (templateAction == Action.SAVE)
            Intent(context, TemplateSaver::class.java).apply {
                action = Intent.ACTION_INSERT
                putExtra(KEY_TEMPLATEID, id)
            } else
            Intent(context, ExpenseEdit::class.java).apply {
                action = ExpenseEdit.ACTION_CREATE_FROM_TEMPLATE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(KEY_TEMPLATEID, id)
                putExtra(EXTRA_START_FROM_WIDGET, true)
                putExtra(EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
            }

    fun buildTemplateShortcut(context: Context, templateInfo: TemplateInfo): ShortcutInfoCompat {
        val title = templateInfo.title.takeIf { it.isNotEmpty() } ?: context.getString(R.string.template)
        return ShortcutInfoCompat.Builder(context, idTemplate(templateInfo.rowId))
            .setShortLabel(title)
            .setLongLabel(title)
            .setIntent(buildTemplateIntent(context, templateInfo.rowId, templateInfo.defaultAction))
            .setIcon(
                IconCompat.createWithResource(
                    context,
                    R.drawable.ic_menu_template_shortcut
                )
            )
            .build()
    }
}
