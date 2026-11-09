package org.totschnig.myexpenses.compose.accounts

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AmountText
import org.totschnig.myexpenses.compose.CheckableMenuEntry
import org.totschnig.myexpenses.compose.ColorCircle
import org.totschnig.myexpenses.compose.DonutInABox
import org.totschnig.myexpenses.compose.ExpansionHandle
import org.totschnig.myexpenses.compose.ExpansionHandler
import org.totschnig.myexpenses.compose.HierarchicalMenu
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyContext
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.LocalDateFormatter
import org.totschnig.myexpenses.compose.MenuEntry.Companion.delete
import org.totschnig.myexpenses.compose.MenuEntry.Companion.edit
import org.totschnig.myexpenses.compose.MenuEntry.Companion.toggle
import org.totschnig.myexpenses.compose.OverFlowMenu
import org.totschnig.myexpenses.compose.SubMenuEntry
import org.totschnig.myexpenses.compose.TEST_TAG_ACCOUNTS
import org.totschnig.myexpenses.compose.TEST_TAG_DELETE_ACCOUNT
import org.totschnig.myexpenses.compose.TEST_TAG_EDIT_ACCOUNT
import org.totschnig.myexpenses.compose.UiText
import org.totschnig.myexpenses.compose.conditional
import org.totschnig.myexpenses.compose.main.deltaLabel
import org.totschnig.myexpenses.compose.main.validatedBalanceType
import org.totschnig.myexpenses.compose.optional
import org.totschnig.myexpenses.compose.scrollbar.LazyColumnWithScrollbar
import org.totschnig.myexpenses.dialog.Percent
import org.totschnig.myexpenses.model.AccountFlag
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.AccountGroupingKey
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.BalanceType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.DEFAULT_FLAG_ID
import org.totschnig.myexpenses.provider.PORTFOLIO_CONTAINER
import org.totschnig.myexpenses.util.calculateRealExchangeRate
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.isolateText
import org.totschnig.myexpenses.viewmodel.data.AggregateAccount
import org.totschnig.myexpenses.viewmodel.data.BaseAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import java.text.DecimalFormat
import kotlin.math.absoluteValue

const val SIGMA = "Σ"

sealed class AccountEvent {
    data object Edit : AccountEvent()
    data object Delete : AccountEvent()
    data class SetFlag(val flagId: Long) : AccountEvent()
    data object ToggleSealed : AccountEvent()
    data object ToggleExcludeFromTotals : AccountEvent()
    data object ToggleDynamicExchangeRate : AccountEvent()
    data class ViewPriceHistory(val commodity: String) : AccountEvent()
}

interface AccountEventHandler {
    operator fun invoke(event: AccountEvent, account: FullAccount)
}

@Composable
fun PortfolioInventory(
    portfolio: FullAccount,
    onAccountEvent: AccountEventHandler,
) {
    val fXFormat = remember { DecimalFormat("#.############") }
    val sortedChildren = remember(portfolio.children) {
        portfolio.children.sortedWith(
            compareBy<FullAccount> { it.isPortfolioCash }.thenBy { it.label }
        )
    }
    sortedChildren.forEach { asset ->
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = asset.label,
                modifier = Modifier.weight(1f)
            )

            val isAsset = asset.currencyUnit != portfolio.currencyUnit
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .conditional(isAsset) {
                        clickable {
                            onAccountEvent(
                                AccountEvent.ViewPriceHistory(
                                    asset.currencyUnit.code
                                ),
                                portfolio
                            )
                        }
                    }
            ) {
                // Corrected Valuation in Portfolio Currency
                AmountText(
                    amount = asset.equivalentCurrentBalance,
                    currency = portfolio.currencyUnit,
                )
                // Quantity in Asset Units
                if (isAsset) {

                    asset.latestExchangeRate?.let { (_, rate) ->
                        val realRate = calculateRealExchangeRate(
                            rate,
                            asset.currencyUnit,
                            portfolio.currencyUnit
                        )
                        val priceFormatted = fXFormat.format(realRate)
                        val quantityFormatted = LocalCurrencyFormatter.current.convAmount(
                            asset.currentBalance,
                            asset.currencyUnit
                        ) {
                            it.decimalFormatSymbols = it.decimalFormatSymbols.apply {
                                currencySymbol = ""
                            }
                        }

                        val priceMarker = "[[PRICE]]"
                        val localizedLayout = stringResource(
                            R.string.asset_quantity_at_price,
                            quantityFormatted,
                            priceMarker
                        )

                        val annotatedString = buildAnnotatedString {
                            val priceIndex = localizedLayout.indexOf(priceMarker)
                            if (priceIndex != -1) {
                                append(localizedLayout.substring(0, priceIndex))
                                withStyle(
                                    style = SpanStyle(
                                        textDecoration = TextDecoration.Underline,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    append(priceFormatted)
                                }
                                append(localizedLayout.substring(priceIndex + priceMarker.length))
                            } else {
                                append(localizedLayout)
                            }
                        }

                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.labelSmall
                        )

                    }
                }
            }
        }
    }

    SumRowV2(
        label = R.string.total_value,
        amount = portfolio.effectiveBalance,
        currency = portfolio.currencyUnit,
        highlight = true,
        modifier = Modifier.drawSumLine()
    )
}

@Composable
fun AccountIndicator(
    account: FullAccount,
    bankIcon: @Composable ((Modifier, Long) -> Unit)? = null,
    reducedSizeIfPossible: Boolean = false,
) {
    val fontSize =
        if (reducedSizeIfPossible && account.progress == null && bankIcon == null) 9.sp else 13.sp

    val size = with(LocalDensity.current) { (fontSize * 2).toDp() }

    val color = Color(account.color(resources = LocalResources.current))

    val modifier = Modifier
        .padding(end = 6.dp)
        .size(size)

    account.progress?.let { (sign, progress) ->
        DonutInABox(
            modifier = modifier,
            progress = progress,
            fontSize = 10.sp,
            color = color,
            excessColor = LocalColors.current.amountColor(
                sign
            )
        )
    } ?: run {
        if (account.bankId == null || bankIcon == null) {
            ColorCircle(
                modifier,
                color
            ) {
                //This is only used in V1
                if (account.isAggregate) {
                    Text(fontSize = fontSize, text = SIGMA, color = Color.White)
                }
            }
        } else bankIcon.invoke(modifier, account.bankId)
    }
}

@Composable
fun AccountSummaryV2(
    account: BaseAccount,
    onDisplayBalanceTypeChange: ((BalanceType) -> Unit)? = null,
    onAccountEvent: AccountEventHandler,
) {

    when (account) {
        is FullAccount -> if (account.isPortfolio)
            PortfolioInventory(account, onAccountEvent)
        else
            AccountSummaryV2(
                account,
                onDisplayBalanceTypeChange
            )

        is AggregateAccount -> AccountSummaryV2(
            account,
            onDisplayBalanceTypeChange
        )
    }
}


@Composable
fun AccountSummaryV2(
    account: FullAccount,
    onDisplayBalanceTypeChange: ((BalanceType) -> Unit)? = null,
) {
    val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
    val isFx = account.currency != homeCurrency.code
    val showEquivalentValues = isFx && (account.dynamic || account.hasInitialExchangeRate)
    val balanceType = account.validatedBalanceType

    if (account.openingBalance != 0L) {
        SumRowV2(
            label = R.string.opening_balance,
            amount = account.openingBalance,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentOpeningBalance.takeIf {
                isFx && account.hasInitialExchangeRate
            }
        )
    }

    if (account.sumIncome != 0L) {
        SumRowV2(
            label = R.string.sum_income,
            amount = account.sumIncome,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumIncome
                .takeIf { showEquivalentValues }
        )
    }

    if (account.sumExpense != 0L) {
        SumRowV2(
            label = R.string.sum_expenses,
            amount = account.sumExpense,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumExpense
                .takeIf { showEquivalentValues }
        )
    }

    if (account.sumTransfer != 0L) {
        SumRowV2(
            label = R.string.sum_transfer,
            amount = account.sumTransfer,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumTransfer
                .takeIf { showEquivalentValues }
        )
    }

    val hasMultipleBalanceTypeOptions =
        account.total != null || account.type.supportsReconciliation || account.criterion != null

    account.total?.let {
        SumRowV2(
            label = R.string.menu_aggregates,
            amount = account.total,
            currency = account.currencyUnit,
            modifier = Modifier.drawSumLine(),
            formattedEquivalentAmount = account.equivalentTotal
                .takeIf { showEquivalentValues },
            highlight = balanceType == BalanceType.TOTAL,
            onClick = onDisplayBalanceTypeChange?.let { { it(BalanceType.TOTAL) } }
        )
    }

    SumRowV2(
        label = R.string.current_balance,
        amount = account.currentBalance,
        currency = account.currencyUnit,
        modifier = Modifier.conditional(account.total == null) {
            drawSumLine()
        },
        formattedEquivalentAmount = account.equivalentCurrentBalance
            .takeIf { showEquivalentValues },
        highlight = balanceType == BalanceType.CURRENT,
        onClick = if (hasMultipleBalanceTypeOptions) {
            onDisplayBalanceTypeChange?.let { { it(BalanceType.CURRENT) } }
        } else null
    )

    account.criterion?.let { criterion ->
        val delta = account.currentBalance - criterion
        val deltaPercent = delta.toFloat() / criterion

        SumRowV2(
            label = account.deltaLabel,
            amount = delta,
            currency = account.currencyUnit,
            percent = deltaPercent.absoluteValue,
            highlight = balanceType == BalanceType.DELTA,
            onClick = onDisplayBalanceTypeChange?.let { { it(BalanceType.DELTA) } }
        )
        SumRowV2(
            label = if (account.criterion > 0) R.string.saving_goal else R.string.credit_limit,
            amount = account.criterion,
            currency = account.currencyUnit,
        )
    }

    if (account.type.supportsReconciliation) {
        SumRowV2(
            label = R.string.total_cleared,
            amount = account.clearedTotal,
            currency = account.currencyUnit,
            highlight = balanceType == BalanceType.CLEARED,
            onClick = onDisplayBalanceTypeChange?.let { { it(BalanceType.CLEARED) } }
        )
        SumRowV2(
            label = R.string.total_reconciled,
            amount = account.reconciledTotal,
            currency = account.currencyUnit,
            highlight = balanceType == BalanceType.RECONCILED,
            onClick = onDisplayBalanceTypeChange?.let { { it(BalanceType.RECONCILED) } }
        )
    }
}

@Composable
fun AccountSummaryV2(
    account: AggregateAccount,
    onDisplayBalanceTypeChange: ((BalanceType) -> Unit)? = null,
) {
    val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
    val isFx = account.currency != homeCurrency.code
    val balanceType = account.validatedBalanceType

    SumRowV2(
        label = R.string.opening_balance,
        amount = account.openingBalance ?: account.equivalentOpeningBalance,
        currency = account.currencyUnit,
        formattedEquivalentAmount = account.equivalentOpeningBalance.takeIf { isFx }
    )

    (account.sumIncome ?: account.equivalentSumIncome).takeIf { it != 0L }?.let {
        SumRowV2(
            label = R.string.sum_income,
            amount = it,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumIncome.takeIf { isFx }
        )
    }

    (account.sumExpense ?: account.equivalentSumExpense).takeIf { it != 0L }?.let {
        SumRowV2(
            label = R.string.sum_expenses,
            amount = it,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumExpense.takeIf { isFx }
        )
    }

    (account.sumTransfer ?: account.equivalentSumTransfer).takeIf { it != 0L }?.let {
        SumRowV2(
            label = R.string.sum_transfer,
            amount = it,
            currency = account.currencyUnit,
            formattedEquivalentAmount = account.equivalentSumTransfer.takeIf { isFx }
        )
    }

    val displayTotal = account.total ?: account.equivalentTotal.takeIf { it != 0L }

    val hasMultipleBalanceTypeOptions = displayTotal != null

    displayTotal?.let {
        SumRowV2(
            label = R.string.menu_aggregates,
            amount = it,
            currency = account.currencyUnit,
            modifier = Modifier.drawSumLine(),
            formattedEquivalentAmount = account.equivalentTotal.takeIf { isFx },
            highlight = balanceType == BalanceType.TOTAL,
            onClick = onDisplayBalanceTypeChange?.let { { it(BalanceType.TOTAL) } }
        )
    }

    SumRowV2(
        label = R.string.current_balance,
        amount = account.currentBalance ?: account.equivalentCurrentBalance,
        currency = account.currencyUnit,
        modifier = Modifier.conditional(displayTotal == null) {
            drawSumLine()
        },
        formattedEquivalentAmount = account.equivalentCurrentBalance.takeIf { isFx },
        highlight = balanceType == BalanceType.CURRENT,
        onClick = if (hasMultipleBalanceTypeOptions) {
            onDisplayBalanceTypeChange?.let { { it(BalanceType.CURRENT) } }
        } else null
    )
}
@Composable
fun SumRowV2(
    label: Int,
    amount: Long,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier,
    formattedEquivalentAmount: Long? = null,
    percent: Float? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val fontWeight = if (highlight) FontWeight.Bold else null
    val auxiliaryTextStyle = MaterialTheme.typography.labelSmall
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .optional(onClick) { clickable(onClick = it) },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (onClick != null) {
            RadioButton(
                modifier = Modifier.padding(end = 2.dp),
                selected = highlight,
                onClick = null // handled by Surface
            )
        }
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            fontWeight = fontWeight,
        )
        Column(modifier, horizontalAlignment = Alignment.End) {
            AmountText(amount, currency)
            formattedEquivalentAmount?.let {
                AmountText(
                    it,
                    LocalCurrencyContext.current.homeCurrencyUnit,
                    fontSize = auxiliaryTextStyle.fontSize
                )
            }
            percent?.let { Percent(it, style = auxiliaryTextStyle) }
        }
    }
}

@Composable
fun Modifier.drawSumLine(): Modifier {
    val borderColor = MaterialTheme.colorScheme.onSurface
    return drawBehind {
        val strokeWidth = 2 * density
        drawLine(
            borderColor,
            Offset(0f, 0f),
            Offset(size.width, 0f),
            strokeWidth
        )
    }
}

@Preview(fontScale = 2f)
@Composable
fun AccountIndicatorPreview() {
    AccountIndicator(
        account = FullAccount(
            id = 1,
            label = "Account",
            description = "Description",
            currencyUnit = CurrencyUnit.DebugInstance,
            color = android.graphics.Color.RED,
            openingBalance = 0,
            currentBalance = 1000,
            sumIncome = 2000,
            sumExpense = 1000,
            sealed = true,
            type = AccountType.CASH,
            criterion = 5000,
            excludeFromTotals = true
        )
    )
}
