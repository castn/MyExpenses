package org.totschnig.myexpenses.compose.transactions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.io.ImportDateFormat
import org.totschnig.myexpenses.io.ImportTradesParseResult
import org.totschnig.myexpenses.io.ImportTradesParser
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTradesDialog(
    onDismiss: () -> Unit,
    onImport: suspend (List<TradeIntent>, (Int) -> Unit) -> Unit,
    portfolio: FullAccount,
    assets: List<CurrencyUnit>,
    fundingAccounts: List<Pair<Long, String>>,
    roundingMode: RoundingMode = RoundingMode.HALF_UP,
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fundingSource by remember { mutableStateOf(FundingSource.PORTFOLIO) }
    var fundingAccountId by remember { mutableStateOf<Long?>(null) }
    var dateFormat by remember { mutableStateOf(ImportDateFormat.default) }
    var parseResult by remember { mutableStateOf<ImportTradesParseResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }

    val statusMessages = listOfNotNull(
        parseResult?.intents?.takeIf { it.isNotEmpty() }?.size?.let {
            pluralStringResource(R.plurals.import_prices_success_message, it, it)
        },
        parseResult?.parseFailures?.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.import_prices_failure_message, it, it)
        },
        parseResult?.missingAssets?.takeIf { it.isNotEmpty() }?.let { missing ->
            val count = missing.values.sum()
            val assets = missing.keys.joinToString(", ")
            pluralStringResource(R.plurals.import_trades_missing_assets_message, count, count, assets)
        },
        parseResult?.fatalError?.let {
            stringResource(R.string.parse_error_at_line, parseResult?.fatalErrorLine ?: 0, it)
        },
        parseResult?.takeIf {
            it.intents.isEmpty() && it.parseFailures == 0 && it.missingAssets.isEmpty() && it.fatalError == null
        }?.let {
            stringResource(R.string.no_data)
        }
    )

    val statusMessage = statusMessages.takeIf { it.isNotEmpty() }?.joinToString(" ")

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isImporting,
            dismissOnClickOutside = !isImporting
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val currentResult = parseResult
                if (currentResult == null || currentResult.intents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.menu_import),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = stringResource(R.string.trade_import_format_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedUri?.lastPathSegment
                                ?: stringResource(R.string.import_source_file)
                        )
                    }

                    Text(
                        text = stringResource(R.string.date_format),
                        style = MaterialTheme.typography.labelMedium
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ImportDateFormat.entries.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = dateFormat == format,
                                onClick = { dateFormat = format },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ImportDateFormat.entries.size
                                ),
                                icon = {}
                            ) {
                                Text(
                                    format.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    FundingSourceSelector(
                        label = stringResource(R.string.trade_funding_source),
                        portfolio = portfolio,
                        selectedSource = fundingSource,
                        selectedAccountId = fundingAccountId,
                        accounts = fundingAccounts,
                        onSourceSelected = { source, account ->
                            fundingSource = source
                            fundingAccountId = account
                        }
                    )

                    statusMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val uri = selectedUri
                                if (uri != null) {
                                    scope.launch {
                                        parseResult = ImportTradesParser.parse(
                                            context,
                                            uri,
                                            portfolio,
                                            assets,
                                            fundingSource,
                                            fundingAccountId,
                                            dateFormat,
                                            roundingMode
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedUri != null
                        ) {
                            Text(stringResource(R.string.menu_parse))
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    statusMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .height(300.dp)
                    ) {
                        items(currentResult.intents) { intent ->
                            ListItem(
                                headlineContent = {
                                    Text("${intent.targetAsset.description} (${intent.targetAsset.code})")
                                },
                                supportingContent = {
                                    Text("${intent.date.toLocalDate()} - ${stringResource(intent.type.label)}")
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(intent.quantity.toString() + " x")
                                        Text(intent.price.toString())
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isImporting) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "$importProgress/${currentResult.intents.size}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isImporting = true
                                    onImport(currentResult.intents) { progress ->
                                        importProgress = progress
                                    }
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isImporting
                        ) {
                            Text(stringResource(R.string.menu_import))
                        }

                        OutlinedButton(
                            onClick = {
                                parseResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isImporting
                        ) {
                            Text(stringResource(R.string.menu_back))
                        }
                    }
                }
            }
        }
    }
}
