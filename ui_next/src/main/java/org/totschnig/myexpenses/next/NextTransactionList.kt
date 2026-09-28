package org.totschnig.myexpenses.next

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.compose.HierarchicalMenu
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.Menu
import org.totschnig.myexpenses.compose.SubMenuEntry
import org.totschnig.myexpenses.compose.conditional
import org.totschnig.myexpenses.compose.transactions.FutureCriterion
import org.totschnig.myexpenses.compose.transactions.SelectionHandler
import org.totschnig.myexpenses.compose.transactions.TransactionEvent
import org.totschnig.myexpenses.compose.transactions.TransactionEventHandler
import org.totschnig.myexpenses.compose.transactions.TransactionListContent
import org.totschnig.myexpenses.compose.transactions.transactionMenu
import org.totschnig.myexpenses.compose.transactions.voidMarker
import org.totschnig.myexpenses.designsystem.GroupPosition
import org.totschnig.myexpenses.designsystem.Shapes
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.sort.SortDirection
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.viewmodel.data.HeaderData
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/**
 * Transaction list of the new design: transactions are grouped by day, each day has a header
 * with the date and the balance of the account at the end of that day.
 * Expects the account of [content] to be grouped by [Grouping.DAY], otherwise day headers are omitted.
 *
 * @param isReadOnly for lists other than of an account: transactions cannot be selected, tapping
 * them only opens the details, if there are [detailsContent]. The details have a menu only if
 * [TransactionListContent.modificationAllowed], without selecting and filtering, which belong to
 * the list of an account.
 * @param showDateAndAccount second line shows date and account instead of details, for lists
 * without day headers that span several accounts
 * @param detailsContent if given, tapping a transaction opens its details instead of its menu,
 * which then is found in the details. Adds content to the details, e.g. the contract of the transaction.
 */
@Composable
fun NextTransactionList(
    content: TransactionListContent,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    showDateAndAccount: Boolean = false,
    detailsContent: (@Composable (Transaction2) -> Unit)? = null,
) {
    val lazyPagingItems = content.lazyPagingItems
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    // The last version found is kept while the list reloads, e.g. after editing the transaction
    // from its details. Not observed, it only bridges until the list has the transaction again.
    val lastOpened = remember { arrayOfNulls<Transaction2>(1) }
    val opened = openedId?.let { id ->
        lazyPagingItems.itemSnapshotList.find { it?.id == id } ?: lastOpened[0]?.takeIf { it.id == id }
    }
    lastOpened[0] = opened
    val context = LocalContext.current
    val currencyFormatter = LocalCurrencyFormatter.current
    if (detailsContent != null) opened?.let { transaction ->
        TransactionDetailDialog(
            transaction = transaction,
            menu = {
                transactionMenu(
                    content.modificationAllowed,
                    content.accountCount,
                    context,
                    currencyFormatter,
                    transaction,
                    object : TransactionEventHandler {
                        override fun invoke(event: TransactionEvent, transaction: Transaction2) {
                            // Nothing left to show
                            if (event == TransactionEvent.Delete) openedId = null
                            content.onEvent(event, transaction)
                        }
                    }
                ).let { if (isReadOnly) it.withoutListActions() else it }
            }.takeIf { !isReadOnly || content.modificationAllowed },
            onDismiss = { openedId = null }
        ) {
            detailsContent(transaction)
        }
    }
    val headerData = content.headerData as? HeaderData
    val withDayHeaders = headerData?.account?.grouping == Grouping.DAY

    if (lazyPagingItems.itemCount == 0) {
        if (lazyPagingItems.loadState.refresh !is LoadState.Loading) {
            Text(
                text = stringResource(
                    if (content.isFiltered) R.string.next_no_matching_transactions
                    else R.string.next_no_transactions
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
            )
        }
        return
    }

    val futureCriterionDate = when (content.futureCriterion) {
        FutureCriterion.Current -> ZonedDateTime.now(ZoneId.systemDefault())
        FutureCriterion.EndOfDay -> LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault())
    }

    LazyColumn(
        modifier = modifier,
        // Keep the last transaction clear of the FAB
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        val snapshot = lazyPagingItems.itemSnapshotList
        val firstLoadedIndex = snapshot.indexOfFirst { it != null }.coerceAtLeast(0)
        val lastLoadedIndex = snapshot.indexOfLast { it != null }

        // Paging requires a stable number of items to keep up with refreshes in the middle of the list,
        // so unloaded transactions and their headers before the loaded range get empty placeholders
        if (firstLoadedIndex > 0) {
            items(count = firstLoadedIndex, key = { "p_$it" }) {}
        }

        var lastHeaderId: Int? = null
        for (index in firstLoadedIndex..lastLoadedIndex) {
            val item = snapshot[index] ?: continue
            val headerId = item.headerId

            if (withDayHeaders && lastHeaderId == null) {
                val placeholderCount = headerData.groups.keys.count {
                    if (headerData.account.sortDirection == SortDirection.DESC) it > headerId else it < headerId
                }
                repeat(placeholderCount) {
                    item(key = "placeholder_header_$it") {}
                }
            }

            val isFirstInGroup = if (withDayHeaders) headerId != lastHeaderId else lastHeaderId == null
            if (withDayHeaders && isFirstInGroup) {
                item(key = "header_$headerId") {
                    DayHeader(
                        date = item.date.toLocalDate(),
                        balance = headerData.groups[headerId]?.interimBalance
                    )
                }
            }

            val nextItem = if (index + 1 < lazyPagingItems.itemCount) lazyPagingItems.peek(index + 1) else null
            val isLastInGroup = nextItem == null || (withDayHeaders && nextItem.headerId != headerId)

            item(key = "trans_${item.id}") {
                // Accessing the item through the index triggers loading of further pages
                lazyPagingItems[index]?.let { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        isFirst = isFirstInGroup,
                        isLast = isLastInGroup,
                        isFuture = transaction.date >= futureCriterionDate,
                        isReadOnly = isReadOnly,
                        showDateAndAccount = showDateAndAccount,
                        onOpen = if (detailsContent != null) {
                            { openedId = transaction.id }
                        } else null,
                        selectionHandler = content.selectionHandler,
                        menu = {
                            transactionMenu(
                                content.modificationAllowed,
                                content.accountCount,
                                context,
                                currencyFormatter,
                                transaction,
                                content.onEvent
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            lastHeaderId = headerId
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    balance: Money?,
) {
    val today = LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (date) {
                today -> stringResource(R.string.next_today)
                today.minusDays(1) -> stringResource(R.string.next_yesterday)
                else -> date.format(formatter)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (balance != null) {
            Text(
                text = LocalCurrencyFormatter.current.convAmount(balance.amountMinor, balance.currencyUnit),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * One transaction. Transactions of a day look like one card: only the outer corners are rounded,
 * and dividers separate the rows. Like in the classic list, a tap opens the menu of the transaction
 * and a long press starts the selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionItem(
    transaction: Transaction2,
    isFirst: Boolean,
    isLast: Boolean,
    isFuture: Boolean,
    isReadOnly: Boolean,
    showDateAndAccount: Boolean,
    /** Opens the details, if null, tapping opens the menu */
    onOpen: (() -> Unit)?,
    selectionHandler: SelectionHandler?,
    menu: () -> Menu,
    modifier: Modifier = Modifier,
) {
    val showMenu = rememberSaveable { mutableStateOf(false) }
    val isSelectable = selectionHandler?.isSelectable(transaction) == true
    val isSelected = selectionHandler?.isSelected(transaction) == true

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Shapes.groupItem(GroupPosition(isFirst, isLast)),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column {
            if (!isFirst) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .conditional(isReadOnly && onOpen != null) {
                        clickable(onClick = onOpen!!)
                    }
                    .conditional(!isReadOnly) {
                        combinedClickable(
                            onLongClick = if (isSelectable) {
                                { selectionHandler.toggle(transaction) }
                            } else null,
                            onClick = {
                                if ((selectionHandler?.selectionCount ?: 0) == 0) {
                                    if (onOpen != null) onOpen() else showMenu.value = true
                                } else if (isSelectable) {
                                    selectionHandler.toggle(transaction)
                                }
                            }
                        )
                    }
                    .voidMarker(transaction.crStatus)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Planned transactions in the future are shown faded
                    .alpha(if (isFuture) 0.6f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                val (title, details) = transaction.titleAndSubtitle()
                val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
                val subtitle = if (showDateAndAccount) listOfNotNull(
                    dateFormatter.format(transaction.date),
                    transaction.accountLabel?.takeIf { it.isNotBlank() }
                ).joinToString(" · ") else details
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title ?: "–",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                SignedAmount(
                    money = transaction.displayAmount,
                    modifier = Modifier.padding(start = 8.dp)
                )
                HierarchicalMenu(showMenu, remember(transaction, showMenu.value) {
                    if (showMenu.value) menu() else emptyList()
                })
            }
        }
    }
}

/**
 * Amount with explicit sign: incoming money green with "+", outgoing money red with "−"
 */
@Composable
internal fun SignedAmount(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val amount = money.amountMinor
    val colors = LocalColors.current
    val formatted = LocalCurrencyFormatter.current.convAmount(amount.absoluteValue, money.currencyUnit)
    Text(
        text = when {
            amount > 0 -> "+ $formatted"
            amount < 0 -> "− $formatted"
            else -> formatted
        },
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = when {
            amount > 0 -> colors.income
            amount < 0 -> colors.expense
            else -> MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * The payee is the main information about a transaction. Without payee, the counterpart
 * account of a transfer or the category take its place.
 */
@Composable
private fun Transaction2.titleAndSubtitle(): Pair<String?, String?> {
    val transferLabel = transferAccountLabel?.let {
        if (displayAmount.amountMinor > 0) "← $it" else "→ $it"
    }
    val categoryLabel = if (isSplit) stringResource(org.totschnig.myexpenses.R.string.split_transaction)
    else categoryPath
    val candidates = listOfNotNull(
        party?.name,
        transferLabel,
        categoryLabel,
        comment?.takeIf { it.isNotBlank() }
    )
    return candidates.firstOrNull() to candidates.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Leaves out the actions that work on the list of an account: selecting, and the filter submenu
 */
private fun Menu.withoutListActions() = filterNot { it.command == "SELECT_TRANSACTION" || it is SubMenuEntry }

