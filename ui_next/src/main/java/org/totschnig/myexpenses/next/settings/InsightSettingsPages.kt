package org.totschnig.myexpenses.next.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.RemovableRow
import org.totschnig.myexpenses.next.core.common.R

/**
 * A rule as the settings list it
 *
 * @param counterpart the payees or merchants the rule is for
 * @param target the category or tag it assigns
 */
data class RuleItem(val counterpart: String, val target: String, val remove: () -> Unit)

/**
 * The rules of categories or tags, each can be deleted
 *
 * @param rules null while they are read
 */
@Composable
internal fun RulesPage(title: String, hint: String, rules: List<RuleItem>?, onBack: () -> Unit) {
    DetailScaffold(onBack = onBack, title = title) {
        DetailCard(Modifier.padding(top = 8.dp)) {
            HintRow(hint, isFirst = true)
            if (rules?.isEmpty() == true) {
                HintRow(stringResource(R.string.next_settings_no_rules))
            }
            val removeLabel = stringResource(R.string.next_settings_remove_rule)
            rules?.forEach { rule ->
                RemovableRow(rule.counterpart, rule.target, removeLabel = removeLabel, onRemove = rule.remove)
            }
        }
    }
}
