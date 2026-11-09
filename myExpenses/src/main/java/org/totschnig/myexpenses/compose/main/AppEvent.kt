package org.totschnig.myexpenses.compose.main

import androidx.annotation.IdRes
import org.totschnig.myexpenses.compose.transactions.Action
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.sort.TransactionSort
import org.totschnig.myexpenses.viewmodel.data.TradeIntent

sealed class AppEvent {
    object CreateAccount : AppEvent()
    object CreatePortfolio : AppEvent()
    data class CreateTransaction(val action: Action, val transferEnabled: Boolean = true) :
        AppEvent()

    data class SetAccountGrouping(val newGrouping: AccountGrouping<*>) : AppEvent()
    data class SetTransactionGrouping(val grouping: Grouping) : AppEvent()
    data class SetTransactionSort(val transactionSort: TransactionSort) : AppEvent()
    object PrintBalanceSheet : AppEvent()
    data class ContextMenuItemClicked(@param:IdRes val itemId: Int) : AppEvent()
    data class MenuItemClicked(@param:IdRes val itemId: Int, val tag: Any? = null) : AppEvent()
    object Sort : AppEvent()
    data class CopyToClipBoard(val text: String) : AppEvent()
    object ToggleNavigation : AppEvent()
    data class SaveTrade(val intent: TradeIntent) : AppEvent()
    data class ShowUpgrade(val feature: ContribFeature) : AppEvent()
}

interface AppEventHandler {
    operator fun invoke(event: AppEvent)
}
