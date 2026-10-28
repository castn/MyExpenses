package org.totschnig.myexpenses.next.feature.tags

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.MultiChoiceSheet
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.next.core.common.NO_NAME
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.tags.Trip

/** Payees named as examples of what a trip was found by */
private const val EXAMPLES = 2

/**
 * Asks in the overview whether the latest suggested [Trip] was one. Marking it tags its expenses
 * with a new tag for this trip, after choosing a name and leaving out expenses that do not belong.
 */
@Composable
fun TripBanner(viewModel: TagDetectionsViewModel, currency: CurrencyInfo, modifier: Modifier = Modifier) {
    val trip = viewModel.trips.collectAsStateWithLifecycle().value.firstOrNull() ?: return
    var reviewing by rememberSaveable(trip.key) { mutableStateOf(false) }
    val dateFormatter = rememberDateFormatter(FormatStyle.LONG)
    val examples = trip.signals.mapNotNull { it.payeeName?.takeIf(String::isNotBlank) }.distinct().take(EXAMPLES)
    val count = trip.transactions.size
    Banner(
        title = stringResource(R.string.next_trip_question),
        messages = listOfNotNull(
            pluralStringResource(
                R.plurals.next_trip_text, count,
                dateFormatter.format(trip.start), dateFormatter.format(trip.end), count
            ),
            examples.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.next_trip_examples, it.joinToString(", "))
            }
        ),
        modifier = modifier
    ) {
        TextButton(onClick = { viewModel.rejectTrip(trip) }) { Text(stringResource(R.string.next_trip_reject)) }
        TextButton(onClick = { reviewing = true }) { Text(stringResource(R.string.next_trip_accept)) }
    }
    if (reviewing) {
        TripSheet(
            trip = trip,
            currency = currency,
            onConfirm = { name, ids ->
                viewModel.acceptTrip(name, ids)
                reviewing = false
            },
            onDismiss = { reviewing = false }
        )
    }
}

/**
 * Names the trip and leaves out expenses that do not belong to it, e.g. the rent paid meanwhile
 */
@Composable
private fun TripSheet(
    trip: Trip,
    currency: CurrencyInfo,
    onConfirm: (String, Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val month = rememberMonthFormatter().format(trip.start)
    val defaultName = stringResource(R.string.next_trip_default_name, month)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var selected by rememberSaveable { mutableStateOf(trip.transactions.mapTo(HashSet()) { it.id }.toSet()) }
    val byId = trip.transactions.associateBy { it.id }
    val dateFormatter = rememberDateFormatter(FormatStyle.SHORT)
    val formatter = LocalAmountFormatter.current
    MultiChoiceSheet(
        title = stringResource(R.string.next_trip_accept),
        options = trip.transactions.map { it.id },
        selected = selected,
        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
        label = { id ->
            val transaction = byId.getValue(id)
            dateFormatter.format(transaction.date) + " · " +
                    (transaction.payeeName?.takeIf(String::isNotBlank) ?: transaction.comment ?: NO_NAME)
        },
        supportingText = { id -> formatter.format(byId.getValue(id).amount.absoluteValue, currency) },
        onConfirm = { onConfirm(name.trim(), selected) },
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && selected.isNotEmpty(),
        header = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.next_trip_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Text(stringResource(R.string.next_trip_choose))
        }
    )
}
