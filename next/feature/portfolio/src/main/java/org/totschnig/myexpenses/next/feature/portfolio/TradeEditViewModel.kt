package org.totschnig.myexpenses.next.feature.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.Funding
import org.totschnig.myexpenses.next.data.portfolio.MatchingTransaction
import org.totschnig.myexpenses.next.data.portfolio.Portfolio
import org.totschnig.myexpenses.next.data.portfolio.Trade
import org.totschnig.myexpenses.next.data.portfolio.TradeAccounts
import org.totschnig.myexpenses.next.data.portfolio.TradeDraft
import org.totschnig.myexpenses.next.data.portfolio.TradeFigures
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.data.portfolio.TradeStore
import org.totschnig.myexpenses.next.data.portfolio.asset
import org.totschnig.myexpenses.next.data.portfolio.figures
import org.totschnig.myexpenses.next.data.portfolio.invertTransfer
import org.totschnig.myexpenses.next.data.portfolio.linkAfter
import org.totschnig.myexpenses.next.data.portfolio.matchQuery
import org.totschnig.myexpenses.next.data.portfolio.newTradeDraft
import org.totschnig.myexpenses.next.data.portfolio.next
import org.totschnig.myexpenses.next.data.portfolio.toDraft
import org.totschnig.myexpenses.next.data.portfolio.toValues
import org.totschnig.myexpenses.next.data.portfolio.withAsset
import org.totschnig.myexpenses.next.data.portfolio.withFunding
import org.totschnig.myexpenses.next.data.portfolio.withKind

/** What the screen of a trade tells in its snackbar */
enum class TradeMessage { SAVED, SAVE_FAILED, ASSET_TAKEN }

/**
 * What the form of a trade shows
 *
 * @param assets the assets known to the app that are not held in the portfolio
 * @param figures principal, costs, total and what is available, null while the portfolio loads
 * @param matches transactions of the funding account that may be the other side of the trade
 * @param isNotFound the trade to change is none, e.g. deleted meanwhile
 * @param isDone the trade was saved with "save", the screen closes
 * @param message to show once, e.g. after "save and new"
 */
data class TradeEditState(
    val draft: TradeDraft? = null,
    val portfolio: Portfolio? = null,
    val original: Trade? = null,
    val asset: Asset? = null,
    val assets: List<Asset> = emptyList(),
    val accounts: TradeAccounts = TradeAccounts(emptyList(), emptyList()),
    val figures: TradeFigures? = null,
    val matches: List<MatchingTransaction> = emptyList(),
    val isNotFound: Boolean = false,
    val isDone: Boolean = false,
    val message: TradeMessage? = null
)

/**
 * What the user can do in the form of a trade. The costs are typed in the draft, see [changeDraft].
 */
interface TradeFormActions {
    /** A value typed, e.g. the quantity, a cost or the comment */
    fun changeDraft(draft: TradeDraft)

    fun chooseKind(kind: TradeKind)

    fun invertTransfer()

    fun chooseAsset(code: String)

    /** Adds [asset] to the app and chooses it, or tells [TradeMessage.ASSET_TAKEN] */
    fun createAsset(asset: Asset)

    fun chooseFunding(funding: Funding, accountId: Long?)

    fun chooseRounding(mode: RoundingMode)

    /** Takes [transactionId] of the matches as the other side of the trade, null for none */
    fun link(transactionId: Long?)

    fun save(andNew: Boolean = false)

    fun messageShown()
}

/**
 * The form of a new trade or of one changed. What is typed is kept in the [SavedStateHandle]; the
 * lists to choose from are loaded again after Android ended the process.
 *
 * @param now the moment a new trade starts with
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradeEditViewModel(
    private val store: TradeStore,
    private val savedState: SavedStateHandle,
    private val decimalSeparator: Char,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    TradeFormActions {

    private data class Lists(
        val original: Trade? = null,
        val assets: List<Asset> = emptyList(),
        val accounts: TradeAccounts = TradeAccounts(emptyList(), emptyList())
    )

    private val draft = savedState.getStateFlow<TradeDraft?>(KEY_DRAFT, null)
    private val lists = MutableStateFlow(Lists())
    private val status = MutableStateFlow(TradeEditState())

    private val portfolioId = draft.map { it?.portfolioId }.distinctUntilChanged()
    private val portfolio = portfolioId.flatMapLatest { id ->
        id?.let { store.portfolio(it).flowOn(io) } ?: flowOf(null)
    }

    private val form = combine(draft, portfolio, lists, ::formState)

    private fun formState(draft: TradeDraft?, portfolio: Portfolio?, lists: Lists): TradeEditState {
        val held = portfolio?.holdings.orEmpty().map { it.asset.code }.toSet()
        val asset = portfolio?.let { draft?.asset(it, lists.assets) }
        return TradeEditState(
            draft = draft,
            portfolio = portfolio,
            original = lists.original,
            asset = asset,
            assets = lists.assets.filter { it.code !in held },
            accounts = lists.accounts,
            figures = portfolio?.let { draft?.figures(it, asset, lists.original, it.rounding) }
        )
    }

    // The ids of the matches the link was decided for, so that unlinking the only match stays
    private var matchedIds: List<Long>? = null

    private val matches = form
        .map { state -> state.figures?.let { state.draft?.matchQuery(it) } }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            query?.let { store.matching(it).flowOn(io) } ?: flowOf(emptyList())
        }
        .onEach { found ->
            val ids = found.map { it.id }
            if (ids != matchedIds) {
                matchedIds = ids
                draft.value?.let { savedState[KEY_DRAFT] = it.linkAfter(found) }
            }
        }

    val state: StateFlow<TradeEditState> = combine(form, matches, status) { form, matches, status ->
        form.copy(
            matches = matches,
            isNotFound = status.isNotFound,
            isDone = status.isDone,
            message = status.message
        )
    }.stateWhileShown(viewModelScope, TradeEditState(draft = draft.value))

    private var isStarted = false
    private var isSaving = false

    /** The form for a new trade of [kind] in [portfolioId], unless one is typed already */
    fun startNew(portfolioId: Long, kind: TradeKind) {
        if (isStarted) return
        isStarted = true
        viewModelScope.launch {
            val typed = draft.value
            if (typed != null) {
                load(typed.portfolioId, typed.id.takeIf { !typed.isNew })
                return@launch
            }
            val portfolio = portfolioOf(portfolioId) ?: run {
                status.update { it.copy(isNotFound = true) }
                return@launch
            }
            val fees = withContext(io) { store.lastFeeCategories() }
            savedState[KEY_DRAFT] = newTradeDraft(portfolio, kind, now(), fees, decimalSeparator)
            load(portfolioId, null)
        }
    }

    /** The form for changing trade [tradeId], unless it is typed already */
    fun edit(tradeId: Long) {
        if (isStarted) return
        isStarted = true
        viewModelScope.launch {
            val trade = withContext(io) { store.trade(tradeId) }
            val portfolio = trade?.let { portfolioOf(it.portfolioId) }
            if (trade == null || portfolio == null) {
                status.update { it.copy(isNotFound = true) }
                return@launch
            }
            val loaded = load(trade.portfolioId, tradeId)
            if (draft.value?.id != tradeId) {
                savedState[KEY_DRAFT] =
                    trade.toDraft(loaded.accounts.funding, portfolio.currency, decimalSeparator)
            }
        }
    }

    private suspend fun portfolioOf(id: Long) = withContext(io) { store.portfolio(id).first() }

    private suspend fun load(portfolioId: Long, tradeId: Long?): Lists = withContext(io) {
        val portfolio = store.portfolio(portfolioId).filterNotNull().first()
        Lists(
            original = tradeId?.let { store.trade(it) },
            assets = store.assets(),
            accounts = store.accounts(portfolio)
        )
    }.also { lists.value = it }

    private fun update(change: (TradeDraft) -> TradeDraft) {
        draft.value?.let { savedState[KEY_DRAFT] = change(it) }
    }

    override fun changeDraft(draft: TradeDraft) = update { draft }

    override fun chooseKind(kind: TradeKind) = update { it.withKind(kind) }

    override fun invertTransfer() = update { it.invertTransfer() }

    override fun chooseAsset(code: String) {
        val portfolio = state.value.portfolio ?: return
        update { it.withAsset(code, portfolio, lists.value.original, decimalSeparator) }
    }

    override fun createAsset(asset: Asset) {
        viewModelScope.launch {
            val created = withContext(io) { store.createAsset(asset) }
            if (created == null) {
                status.update { it.copy(message = TradeMessage.ASSET_TAKEN) }
            } else {
                lists.update { it.copy(assets = it.assets + created) }
                update { it.copy(assetCode = created.code) }
            }
        }
    }

    override fun chooseFunding(funding: Funding, accountId: Long?) =
        update { it.withFunding(funding, accountId) }

    override fun chooseRounding(mode: RoundingMode) {
        val portfolioId = draft.value?.portfolioId ?: return
        viewModelScope.launch { withContext(io) { store.setRoundingMode(portfolioId, mode) } }
    }

    override fun link(transactionId: Long?) =
        update { it.copy(linkedTransactionId = transactionId) }

    override fun save(andNew: Boolean) {
        val current = state.value
        val draft = current.draft ?: return
        val portfolio = current.portfolio ?: return
        if (isSaving) return
        val values = draft.toValues(portfolio, current.asset, portfolio.rounding) ?: run {
            status.update { it.copy(message = TradeMessage.SAVE_FAILED) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val saved = withContext(io) { runCatching { store.save(portfolio.id, values) } }
            isSaving = false
            if (saved.isFailure) {
                status.update { it.copy(message = TradeMessage.SAVE_FAILED) }
                return@launch
            }
            val fees = if (andNew) withContext(io) { store.lastFeeCategories() } else null
            val next = fees?.let { draft.next(it) }
            matchedIds = null
            savedState[KEY_DRAFT] = next
            status.update {
                if (next == null) it.copy(isDone = true) else it.copy(message = TradeMessage.SAVED)
            }
        }
    }

    override fun messageShown() = status.update { it.copy(message = null) }

    private companion object {
        const val KEY_DRAFT = "tradeDraft"
    }
}
