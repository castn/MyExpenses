package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.liveData
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.provider.KEY_DEFAULT_ACTION
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.util.ShortcutHelper
import javax.inject.Inject

class TemplatesListViewModel(application: Application) :
    ContentResolvingAndroidViewModel(application) {

    @Inject
    lateinit var exchangeRateHandler: ExchangeRateHandler

    fun updateDefaultAction(itemIds: LongArray, action: Template.Action) =
        liveData(context = coroutineContext()) {
            val result = contentResolver.update(
                TransactionProvider.TEMPLATES_URI,
                ContentValues().apply {
                    put(KEY_DEFAULT_ACTION, action.name)
                },
                "$KEY_ROWID IN (${itemIds.joinToString()})", null
            ) == itemIds.size
            val context = getApplication<MyApplication>()
            val shortcuts = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED
            )
            if (result) {
                itemIds.filter { itemId ->
                    shortcuts.any { it.id == ShortcutHelper.idTemplate(itemId) }
                }.forEach {
                    context.getSystemService(ShortcutManager::class.java).updateShortcuts(
                        listOf(
                            ShortcutInfo.Builder(context, ShortcutHelper.idTemplate(it))
                                .setIntent(
                                    ShortcutHelper.buildTemplateIntent(context, it, action)
                                )
                                .build()
                        )
                    )
                }
            }
            emit(result)
        }

    fun newFromTemplate(vararg templateIds: Long) =
        liveData(context = coroutineContext()) {
            emit(templateIds.map { templateId ->
                repository.instantiateTemplate(
                    exchangeRateHandler,
                    templateId,
                    currencyContext
                )
            }.sumOf { if (it.isFailure) 0 else 1 })
        }

    fun instantiateTemplateForResult(templateId: Long) = liveData(context = coroutineContext()) {
        emit(
            repository.instantiateTemplate(
                exchangeRateHandler, templateId, currencyContext
            )
        )
    }
}
