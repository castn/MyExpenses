package org.totschnig.myexpenses.next.feature.tips

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.contracts.labelRes
import org.totschnig.myexpenses.next.core.ui.receipts.labelRes
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.tips.BankFeeKind
import org.totschnig.myexpenses.next.data.tips.DepositNotReturned
import org.totschnig.myexpenses.next.data.tips.DrugstoreInSupermarkets
import org.totschnig.myexpenses.next.data.tips.EatingOut
import org.totschnig.myexpenses.next.data.tips.ItemGroupRising
import org.totschnig.myexpenses.next.data.tips.Outliers
import org.totschnig.myexpenses.next.data.tips.PotsOverspent
import org.totschnig.myexpenses.next.data.tips.SavingTip
import org.totschnig.myexpenses.next.data.tips.SmallPurchases
import org.totschnig.myexpenses.next.data.tips.Supermarkets
import org.totschnig.myexpenses.next.data.tips.ContractKind

/**
 * Title and text of a tip, shared by the list and the card of the overview
 *
 * @param amount shown next to the title
 */
internal class TipText(val title: String, val message: String, val hint: String?, val amount: String)

@Composable
internal fun SavingTip.text(currency: CurrencyInfo): TipText {
    val amount = rememberAmountFormat(currency)
    val perYear = stringResource(R.string.next_tips_per_year, amount(yearlyAmount))
    return when (this) {
        is SavingTip.PriceIncreases -> TipText(
            title = stringResource(R.string.next_tips_price_increases_title),
            message = pluralStringResource(
                R.plurals.next_tips_price_increases_text,
                increases.size,
                increases.size,
                amount(monthlyAmount)
            ),
            hint = stringResource(R.string.next_tips_price_increases_hint),
            amount = perYear
        )

        is SavingTip.BankFees -> TipText(
            title = stringResource(R.string.next_tips_bank_fees_title),
            message = stringResource(R.string.next_tips_bank_fees_text, amount(yearlyAmount)),
            hint = stringResource(R.string.next_tips_bank_fees_hint),
            amount = perYear
        )

        is SavingTip.DuplicateContracts -> TipText(
            title = stringResource(kind.titleRes),
            message = pluralStringResource(
                R.plurals.next_tips_duplicates_text,
                contracts.size,
                contracts.size,
                amount(monthlyAmount)
            ),
            hint = stringResource(kind.hintRes),
            amount = perYear
        )

        is SavingTip.LongRunningContracts -> TipText(
            title = stringResource(R.string.next_tips_long_running_title),
            message = pluralStringResource(
                R.plurals.next_tips_long_running_text,
                contracts.size,
                SavingTip.LongRunningContracts.MIN_YEARS.toInt(),
                contracts.size
            ),
            hint = stringResource(R.string.next_tips_long_running_hint),
            amount = perYear
        )

        is SavingTip.FrequentPayments -> TipText(
            title = stringResource(R.string.next_tips_frequent_title),
            message = pluralStringResource(
                R.plurals.next_tips_frequent_text,
                contracts.size,
                contracts.size,
                amount(monthlyAmount)
            ),
            hint = stringResource(R.string.next_tips_frequent_hint),
            amount = perYear
        )

        is SmallPurchases -> TipText(
            title = stringResource(R.string.next_tips_small_title),
            message = pluralStringResource(
                R.plurals.next_tips_small_text, count, count, amount(SmallPurchases.LIMIT), amount(yearlyAmount), amount(monthlyAmount)
            ),
            hint = stringResource(R.string.next_tips_small_hint),
            amount = perYear
        )

        is EatingOut -> TipText(
            title = stringResource(R.string.next_tips_eating_out_title),
            message = stringResource(R.string.next_tips_eating_out_text, amount(recent), amount(usual)),
            hint = stringResource(R.string.next_tips_eating_out_hint),
            amount = perYear
        )

        is Supermarkets -> TipText(
            title = stringResource(R.string.next_tips_supermarkets_title),
            message = stringResource(
                R.string.next_tips_supermarkets_text,
                amount(chains.last().average), chains.last().label,
                amount(chains.first().average), chains.first().label
            ),
            hint = stringResource(R.string.next_tips_supermarkets_hint),
            amount = perYear
        )

        is Outliers -> TipText(
            title = stringResource(R.string.next_tips_outliers_title, rememberMonthFormatter().format(month)),
            message = pluralStringResource(R.plurals.next_tips_outliers_text, categories.size, categories.size, amount(yearlyAmount)),
            hint = stringResource(R.string.next_tips_outliers_hint),
            amount = "+" + amount(yearlyAmount)
        )

        is ItemGroupRising -> TipText(
            title = stringResource(R.string.next_tips_item_group_rising_title, stringResource(group.labelRes)),
            message = pluralStringResource(
                R.plurals.next_tips_item_group_rising_text, ItemGroupRising.RECENT_MONTHS.toInt(),
                (recentShare * 100).roundToInt(), ItemGroupRising.RECENT_MONTHS.toInt(), (usualShare * 100).roundToInt()
            ),
            hint = stringResource(R.string.next_tips_scanned_hint),
            amount = perYear
        )

        is DepositNotReturned -> TipText(
            title = stringResource(R.string.next_tips_deposit_title),
            message = stringResource(R.string.next_tips_deposit_text, amount(paid), amount(returned)),
            hint = stringResource(R.string.next_tips_deposit_hint),
            amount = perYear
        )

        is PotsOverspent -> TipText(
            title = pluralStringResource(R.plurals.next_tips_pots_title, pots.size, pots.first().name, pots.size - 1),
            message = pluralStringResource(R.plurals.next_tips_pots_text, PotsOverspent.PERIODS, PotsOverspent.MIN_OVERSPENT, PotsOverspent.PERIODS),
            hint = stringResource(R.string.next_tips_pots_hint),
            amount = perYear
        )

        is DrugstoreInSupermarkets -> TipText(
            title = stringResource(R.string.next_tips_drugstore_title),
            message = pluralStringResource(R.plurals.next_tips_drugstore_text, itemCount, itemCount, amount(yearlyAmount)),
            hint = stringResource(R.string.next_tips_drugstore_hint),
            amount = perYear
        )
    }
}

@get:StringRes
private val ContractKind.titleRes: Int
    get() = when (this) {
        ContractKind.CAR_INSURANCE -> R.string.next_tips_duplicates_car
        ContractKind.LIABILITY_INSURANCE -> R.string.next_tips_duplicates_liability
        ContractKind.CONTENTS_INSURANCE -> R.string.next_tips_duplicates_contents
        ContractKind.LEGAL_INSURANCE -> R.string.next_tips_duplicates_legal
        ContractKind.VIDEO_STREAMING -> R.string.next_tips_duplicates_video
        ContractKind.MUSIC_STREAMING -> R.string.next_tips_duplicates_music
        ContractKind.CLOUD_STORAGE -> R.string.next_tips_duplicates_cloud
        ContractKind.MOBILE -> R.string.next_tips_duplicates_mobile
        ContractKind.INTERNET -> R.string.next_tips_duplicates_internet
        ContractKind.ELECTRICITY -> R.string.next_tips_duplicates_electricity
        ContractKind.GAS -> R.string.next_tips_duplicates_gas
        ContractKind.GYM -> R.string.next_tips_duplicates_gym
    }

@get:StringRes
private val ContractKind.hintRes: Int
    get() = when (this) {
        ContractKind.CAR_INSURANCE, ContractKind.LIABILITY_INSURANCE, ContractKind.CONTENTS_INSURANCE,
        ContractKind.LEGAL_INSURANCE ->
            R.string.next_tips_duplicates_hint_insurance

        ContractKind.VIDEO_STREAMING, ContractKind.MUSIC_STREAMING, ContractKind.CLOUD_STORAGE, ContractKind.GYM ->
            R.string.next_tips_duplicates_hint_subscription

        ContractKind.MOBILE, ContractKind.INTERNET, ContractKind.ELECTRICITY, ContractKind.GAS ->
            R.string.next_tips_duplicates_hint_utility
    }

@Composable
internal fun BankFeeKind.label(): String = stringResource(
    when (this) {
        BankFeeKind.ACCOUNT -> R.string.next_tips_fee_account
        BankFeeKind.CARD -> R.string.next_tips_fee_card
        BankFeeKind.FOREIGN -> R.string.next_tips_fee_foreign
        BankFeeKind.ATM -> R.string.next_tips_fee_atm
        BankFeeKind.OVERDRAFT -> R.string.next_tips_fee_overdraft
        BankFeeKind.OTHER -> R.string.next_tips_fee_other
    }
)
