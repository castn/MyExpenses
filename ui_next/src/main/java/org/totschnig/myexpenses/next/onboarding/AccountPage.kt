package org.totschnig.myexpenses.next.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.designsystem.AutocompleteField
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.viewmodel.data.Currency
import org.totschnig.myexpenses.next.core.common.R as NextR

/** What the first account can be set up with */
@Immutable
data class AccountChoices(val currencies: List<Currency>)

@Composable
internal fun AccountPage(
    draft: AccountDraft,
    choices: AccountChoices,
    onDraftChange: (AccountDraft) -> Unit,
) {
    IntroPage(
        title = stringResource(NextR.string.next_onboarding_account_title),
        text = stringResource(NextR.string.next_onboarding_account_text),
        hero = { HeroIllustration(DesignR.drawable.illustration_account) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onDraftChange(draft.copy(label = it)) },
                label = { Text(stringResource(NextR.string.next_onboarding_account_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            val selected = choices.currencies.find { it.code == draft.currency }
            if (selected != null) {
                AutocompleteField(
                    label = stringResource(R.string.currency),
                    selected = selected,
                    options = choices.currencies,
                    optionLabel = { "${it.displayName} (${it.code})" },
                    onSelect = { onDraftChange(draft.copy(currency = it.code)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val isInvalid = draft.openingBalance == null
            OutlinedTextField(
                value = draft.amount,
                onValueChange = { onDraftChange(draft.copy(amount = it)) },
                label = { Text(stringResource(R.string.opening_balance)) },
                placeholder = { Text("0") },
                suffix = { Text(draft.currency) },
                singleLine = true,
                isError = isInvalid,
                supportingText = {
                    Text(
                        stringResource(
                            if (isInvalid) NextR.string.next_onboarding_amount_invalid
                            else NextR.string.next_onboarding_amount_hint
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun AccountPagePreview() {
    NextPreviewTheme {
        AccountPage(
            draft = AccountDraft("Haushaltsbuch", "EUR", "12,5"),
            choices = AccountChoices(listOf(Currency("EUR", "Euro"))),
            onDraftChange = {}
        )
    }
}
