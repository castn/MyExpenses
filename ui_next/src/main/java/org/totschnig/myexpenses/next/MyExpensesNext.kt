package org.totschnig.myexpenses.next

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import org.totschnig.myexpenses.activity.MyExpensesV2
import org.totschnig.myexpenses.activity.SplashActivity
import org.totschnig.myexpenses.compose.accounts.AccountEventHandler
import org.totschnig.myexpenses.compose.main.AppEventHandler
import org.totschnig.myexpenses.model.AccountFlag
import org.totschnig.myexpenses.model.AccountGroupingKey
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.PageAccount

/**
 * Entry point of the new UI, started from its own launcher icon ("MyExpenses Next").
 *
 * Reuses all of [MyExpensesV2] (view model, dialogs, event handling) and only replaces
 * what is exposed through its hooks:
 * - [MainTheme]: look and feel ([NextTheme])
 * - [MainScreen]: the main screen ([NextMainScreen])
 * - [dispatchCommand]: navigation to other screens ([NextRouter])
 */
class MyExpensesNext : MyExpensesV2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefHandler.getInt(PrefKey.CURRENT_VERSION, -1) == -1) {
            // Fresh install: onboarding is handled by the regular entry point
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
        }
    }

    @Composable
    override fun MainTheme(content: @Composable () -> Unit) {
        NextTheme(content)
    }

    @Composable
    override fun MainScreen(
        viewModel: MyExpensesV2ViewModel,
        accounts: List<FullAccount>,
        allCurrencies: List<CurrencyUnit>,
        availableFilters: List<AccountGroupingKey>,
        selectedAccountId: Long,
        onAppEvent: AppEventHandler,
        onAccountEvent: AccountEventHandler,
        onPrepareContextMenuItem: (itemId: Int) -> Boolean,
        onPrepareMenuItem: (itemId: Int) -> Boolean,
        flags: List<AccountFlag>,
        bankIcon: (@Composable (Modifier, Long) -> Unit)?,
        adView: @Composable (MutableState<Boolean>) -> Unit,
        isNavigationVisible: Boolean,
        isCurrencyUsed: suspend (String) -> Boolean,
        onCreateAsset: suspend (code: String, symbol: String, fractionDigits: Int, label: String?, commodityType: CommodityType) -> CurrencyUnit?,
        pageContent: @Composable (pageAccount: PageAccount, isCurrent: Boolean) -> Unit,
    ) {
        NextMainScreen(
            viewModel = viewModel,
            accounts = accounts,
            allCurrencies = allCurrencies,
            availableFilters = availableFilters,
            selectedAccountId = selectedAccountId,
            onAppEvent = onAppEvent,
            onAccountEvent = onAccountEvent,
            onPrepareContextMenuItem = onPrepareContextMenuItem,
            onPrepareMenuItem = onPrepareMenuItem,
            flags = flags,
            bankIcon = bankIcon,
            adView = adView,
            isNavigationVisible = isNavigationVisible,
            isCurrencyUsed = isCurrencyUsed,
            onCreateAsset = onCreateAsset,
            pageContent = pageContent
        )
    }

    override fun dispatchCommand(command: Int, tag: Any?): Boolean =
        NextRouter.route(this, command, tag) || super.dispatchCommand(command, tag)
}
