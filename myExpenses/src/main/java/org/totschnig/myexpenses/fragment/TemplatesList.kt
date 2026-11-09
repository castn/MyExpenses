/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.totschnig.myexpenses.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.SparseBooleanArray
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import org.totschnig.myexpenses.model.ContribPayload
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ImageView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.size
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.evernote.android.state.StateSaver
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.activity.EDIT_REQUEST
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.compose.transactions.COMMENT_SEPARATOR
import org.totschnig.myexpenses.databinding.TemplatesListBinding
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.getCurrencyForAccount
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.model.sort.Sort.Companion.preferredOrderByForTemplates
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_TRANSFER
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COLOR
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DEFAULT_ACTION
import org.totschnig.myexpenses.provider.KEY_ORIGINAL_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ORIGINAL_CURRENCY
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PATH
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getIntIfExists
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.provider.isNull
import org.totschnig.myexpenses.util.ICurrencyFormatter
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.enumValueOrDefault
import org.totschnig.myexpenses.util.readPrimaryTextColor
import org.totschnig.myexpenses.viewmodel.TemplatesListViewModel
import org.totschnig.myexpenses.viewmodel.data.RIGHT_ARROW
import javax.inject.Inject
import kotlin.math.absoluteValue

class TemplatesList : SortableListFragment(), LoaderManager.LoaderCallbacks<Cursor?> {
    override val menuResource: Int
        get() = R.menu.templateslist_context
    private var _templatesCursor: Cursor? = null

    val templatesCursor: Cursor
        get() = _templatesCursor!!
    private lateinit var mAdapter: SimpleCursorAdapter
    private lateinit var mManager: LoaderManager
    @Inject
    lateinit var currencyFormatter: ICurrencyFormatter

    @Inject
    lateinit var currencyContext: CurrencyContext

    @Inject
    lateinit var repository: Repository

    val viewModel: TemplatesListViewModel by viewModels()

    private var _binding: TemplatesListBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        StateSaver.restoreInstanceState(this, savedInstanceState)
        val appComponent = (requireActivity().application as MyApplication).appComponent
        appComponent.inject(this)
        appComponent.inject(viewModel)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        StateSaver.saveInstanceState(this, outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireActivity() as ProtectedFragmentActivity
        _binding = TemplatesListBinding.inflate(inflater, container, false)
        mManager = LoaderManager.getInstance(this)
        mManager.initLoader(SORTABLE_CURSOR, null, this)
        mAdapter = MyAdapter(ctx)
        binding.list.adapter = mAdapter
        binding.list.emptyView = binding.empty
        binding.list.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, id: Long ->
            if (_templatesCursor == null || !templatesCursor.moveToPosition(position)) return@setOnItemClickListener
            val isSealed = templatesCursor.getInt(KEY_SEALED) != 0
            if (isSealed) {
                ctx.showSnackBar(R.string.object_sealed)
            }
            if (!isSealed) {
                if (isForeignExchangeTransfer(position)) {
                    dispatchCreateInstanceEditDo(id)
                } else {
                    val splitAtPosition = isSplitAtPosition(position)
                    val defaultAction: Template.Action = enumValueOrDefault(
                        templatesCursor.getString(KEY_DEFAULT_ACTION),
                        Template.Action.SAVE
                    )
                    if (defaultAction == Template.Action.SAVE) {
                        if (splitAtPosition) {
                            requestSplitTransaction(ContribPayload.InstantiateSplitTemplateSave(longArrayOf(id)))
                        } else {
                            dispatchCreateInstanceSaveDo(longArrayOf(id))
                        }
                    } else {
                        if (splitAtPosition) {
                            requestSplitTransaction(ContribPayload.InstantiateSplitTemplateEdit(id))
                        } else {
                            dispatchCreateInstanceEditDo(id)
                        }
                    }
                }
            }
        }
        registerForContextualActionBar(binding.list)
        return binding.root
    }

    private fun bulkUpdateDefaultAction(
        itemIds: LongArray,
        action: Template.Action,
        resultFeedBack: Int,
    ) {
        viewModel.updateDefaultAction(itemIds, action).observe(
            viewLifecycleOwner
        ) { result: Boolean ->
            showSnackbar(
                if (result) getString(resultFeedBack) else "Error while setting default action for template click"
            )
        }
    }

    override fun dispatchCommandMultiple(
        command: Int,
        positions: SparseBooleanArray, itemIds: LongArray,
    ): Boolean {
        if (super.dispatchCommandMultiple(command, positions, itemIds)) {
            return true
        }
        when (command) {
            R.id.DEFAULT_ACTION_EDIT_COMMAND -> {
                bulkUpdateDefaultAction(
                    itemIds,
                    Template.Action.EDIT,
                    R.string.menu_create_instance_edit
                )
                return true
            }

            R.id.DEFAULT_ACTION_SAVE_COMMAND -> {
                bulkUpdateDefaultAction(
                    itemIds,
                    Template.Action.SAVE,
                    R.string.menu_create_instance_save
                )
                return true
            }

            R.id.DELETE_COMMAND -> {
                MessageDialogFragment.newInstance(
                    getString(R.string.dialog_title_warning_delete_template),  //TODO check if template
                    resources.getQuantityString(
                        R.plurals.warning_delete_template,
                        itemIds.size,
                        itemIds.size
                    ),
                    MessageDialogFragment.Button(
                        R.string.menu_delete,
                        R.id.DELETE_COMMAND_DO,
                        itemIds
                    ),
                    null,
                    MessageDialogFragment.Button(
                        R.string.response_no,
                        R.id.CANCEL_CALLBACK_COMMAND,
                        null
                    )
                )
                    .show(requireActivity().supportFragmentManager, "DELETE_TEMPLATE")
                return true
            }

            R.id.CREATE_INSTANCE_SAVE_COMMAND -> {
                if (hasSplitAtPositions(positions)) {
                    requestSplitTransaction(ContribPayload.InstantiateSplitTemplateSave(itemIds))
                } else {
                    dispatchCreateInstanceSaveDo(itemIds)
                }
                finishActionMode()
                return true
            }

            else -> return false
        }
    }

    override fun dispatchCommandSingle(command: Int, info: ContextMenuInfo?): Boolean {
        if (super.dispatchCommandSingle(command, info)) {
            return true
        }
        val menuInfo = info as AdapterContextMenuInfo
        val i: Intent
        if (command == R.id.CREATE_INSTANCE_EDIT_COMMAND) {
            if (isSplitAtPosition(menuInfo.position)) {
                requestSplitTransaction(ContribPayload.InstantiateSplitTemplateEdit(menuInfo.id))
            } else {
                dispatchCreateInstanceEditDo(menuInfo.id)
            }
            finishActionMode()
            return true
        } else if (command == R.id.EDIT_COMMAND) {
            finishActionMode()
            i = Intent(activity, ExpenseEdit::class.java)
            i.putExtra(KEY_TEMPLATEID, menuInfo.id)
            //TODO check what to do on Result
            startActivityForResult(i, EDIT_REQUEST)
            return true
        }
        return false
    }

    private fun isSplitAtPosition(position: Int) = _templatesCursor?.let {
        it.moveToPosition(position) && it.getLongOrNull(KEY_CATID) == SPLIT_CATID
    } ?: false

    private fun hasSplitAtPositions(positions: SparseBooleanArray): Boolean {
        for (i in 0 until positions.size) {
            if (positions.valueAt(i) && isSplitAtPosition(positions.keyAt(i))) {
                return true
            }
        }
        return false
    }

    /**
     * calls [ProtectedFragmentActivity.contribFeatureRequested]
     * for feature [ContribFeature.SPLIT_TRANSACTION]
     *
     * @param payload either [ContribPayload.InstantiateSplitTemplateSave] or [ContribPayload.InstantiateSplitTemplateEdit]
     */
    private fun requestSplitTransaction(payload: ContribPayload?) {
        (requireActivity() as ProtectedFragmentActivity).contribFeatureRequested(
            ContribFeature.SPLIT_TRANSACTION,
            payload
        )
    }

    fun dispatchCreateInstanceSaveDo(templateIds: LongArray) {
        viewModel.newFromTemplate(*templateIds).observe(
            viewLifecycleOwner
        ) { successCount: Int ->
            showSnackbar(
                if (successCount == 0) getString(R.string.save_transaction_error) else resources.getQuantityString(
                    R.plurals.save_transaction_from_template_success,
                    successCount,
                    successCount
                )
            )
        }
    }

    fun dispatchCreateInstanceEditDo(itemId: Long) {
        startActivity(Intent(requireActivity(), ExpenseEdit::class.java).apply {
            action = ExpenseEdit.ACTION_CREATE_FROM_TEMPLATE
            putExtra(KEY_TEMPLATEID, itemId)
        })
    }

    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor?> {
        return if (id == SORTABLE_CURSOR) {
            CursorLoader(
                requireActivity(),
                TransactionProvider.TEMPLATES_URI,
                null,
                "$KEY_PARENTID IS NULL",
                null,
                preferredOrderByForTemplates(prefHandler, Sort.USAGES, prefHandler.collate)
            )
        } else throw IllegalArgumentException("Unexpected loader id: $id")
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, c: Cursor?) {
        if (loader.id == SORTABLE_CURSOR) {
            _templatesCursor = c
            invalidateCAB()
            mAdapter.swapCursor(_templatesCursor)
            requireActivity().invalidateOptionsMenu()
        }
    }

    fun showSnackbar(msg: String) {
        (activity as ProtectedFragmentActivity).showSnackBar(msg)
    }


    fun dispatchDeleteDo(tag: LongArray) {
        showSnackbar(getString(R.string.progress_dialog_deleting))
        viewModel.deleteTemplates(tag)
            .observe(
                viewLifecycleOwner
            ) { result ->
                val activity = requireActivity() as BaseActivity
                activity.showSnackBar(
                    buildList {
                        if (result.success > 0) {
                            add(
                                activity.resources.getQuantityString(
                                    R.plurals.delete_success,
                                    result.success,
                                    result.success
                                )
                            )
                        }
                        if (result.failure > 0) {
                            add(activity.deleteFailureMessage(null))
                        }
                    }.joinToString(" ")
                )
            }
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        if (loader.id == SORTABLE_CURSOR) {
            _templatesCursor = null
            mAdapter.swapCursor(null)
        }
    }

    override fun getSortOrderPrefKey(): PrefKey {
        return PrefKey.SORT_ORDER_TEMPLATES
    }

    private inner class MyAdapter(context: Context) :
        SimpleCursorAdapter(context, R.layout.template_row, null, emptyArray(), intArrayOf(), 0) {
        private val colorExpense: Int =
            ResourcesCompat.getColor(resources, R.color.colorExpense, null)
        private val colorIncome: Int =
            ResourcesCompat.getColor(resources, R.color.colorIncome, null)
        private val colorTransfer: Int =
            ResourcesCompat.getColor(resources, R.color.colorTransfer, null)
        private val colorDefault: Int = readPrimaryTextColor(context)

        @SuppressLint("SetTextI18n")
        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val isSealed = cursor.getInt(KEY_SEALED) != 0
            val isTransfer = !cursor.isNull(KEY_TRANSFER_ACCOUNT)
            val tv1 = view.findViewById<TextView>(R.id.Amount)
            val amount = cursor.getLong(KEY_AMOUNT)
            val type = cursor.getIntIfExists(KEY_TYPE)?.toByte()
            val amountColor = when (type) {
                FLAG_EXPENSE -> colorExpense
                FLAG_INCOME -> colorIncome
                FLAG_TRANSFER -> colorTransfer
                else -> colorDefault
            }
            tv1.setTextColor(amountColor)
            tv1.text = currencyFormatter.convAmount(
                if (isTransfer) amount.absoluteValue else amount,
                currencyContext[cursor.getString(KEY_CURRENCY)]
            )
            val color = cursor.getInt(KEY_COLOR)
            view.findViewById<View>(R.id.colorAccount).setBackgroundColor(color)
            val tv2 = view.findViewById<TextView>(R.id.Category)
            var catText: CharSequence = cursor.getString(KEY_PATH)
            if (isTransfer) {
                val account = cursor.getString(KEY_ACCOUNT_LABEL)
                val transferAccount = cursor.getString(KEY_TRANSFER_ACCOUNT_LABEL)

                val transfer = if (amount < 0) "$account $RIGHT_ARROW $transferAccount"
                else "$transferAccount $RIGHT_ARROW $account"

                catText = if (catText.isEmpty()) transfer
                else TextUtils.concat(catText, " (", transfer, ")")
            }
            //TODO: simplify confer TemplateWidget
            var ssb: SpannableStringBuilder
            val comment = cursor.getStringOrNull(KEY_COMMENT)

            if (!comment.isNullOrEmpty()) {
                ssb = SpannableStringBuilder(comment)
                ssb.setSpan(StyleSpan(Typeface.ITALIC), 0, comment.length, 0)
                catText = if (catText.isEmpty()) ssb else
                    TextUtils.concat(catText, COMMENT_SEPARATOR, ssb)
            }
            val payee = cursor.getStringOrNull(KEY_PAYEE_NAME)
            if (!payee.isNullOrEmpty()) {
                ssb = SpannableStringBuilder(payee)
                ssb.setSpan(UnderlineSpan(), 0, payee.length, 0)
                catText = if (catText.isEmpty()) ssb else
                    TextUtils.concat(catText, COMMENT_SEPARATOR, ssb)
            }
            tv2.text = catText
            view.findViewById<TextView>(R.id.Title).text = cursor.getString(KEY_TITLE)

            val planImage = view.findViewById<ImageView>(R.id.Plan)
            planImage.setImageResource(
                when {
                    isSealed -> R.drawable.ic_lock
                    else -> R.drawable.ic_menu_template
                }
            )
            planImage.contentDescription = getString(R.string.template)

            with(view.findViewById<TextView>(R.id.OriginalAmount)) {
                isVisible = cursor.getStringOrNull(KEY_ORIGINAL_CURRENCY)?.let {
                    text = currencyFormatter.convAmount(
                        cursor.getLong(KEY_ORIGINAL_AMOUNT),
                        currencyContext[it]
                    )
                    setTextColor(amountColor)
                    true
                } ?: false
            }
        }
    }

    override fun configureMenu(menu: Menu, lv: AbsListView) {
        super.configureMenu(menu, lv)
        val id = lv.id
        if (id == R.id.list) {
            val checkedItemPositions = binding.list.checkedItemPositions
            var hasForeignExchangeTransfer = false
            var hasSealed = false
            for (i in 0 until checkedItemPositions.size) {
                if (checkedItemPositions.valueAt(i) && isForeignExchangeTransfer(
                        checkedItemPositions.keyAt(i)
                    )
                ) {
                    hasForeignExchangeTransfer = true
                    break
                }
            }
            for (i in 0 until checkedItemPositions.size) {
                if (checkedItemPositions.valueAt(i) && isSealed(checkedItemPositions.keyAt(i))) {
                    hasSealed = true
                    break
                }
            }
            configureMenuInternal(
                menu,
                lv.checkedItemCount,
                hasForeignExchangeTransfer,
                hasSealed
            )
        }
    }

    private fun configureMenuInternal(
        menu: Menu,
        count: Int,
        foreignExchangeTransfer: Boolean,
        hasSealed: Boolean,
    ) {
        menu.findItem(R.id.CREATE_INSTANCE_SAVE_COMMAND).isVisible =
            !foreignExchangeTransfer && !hasSealed
        menu.findItem(R.id.CREATE_INSTANCE_EDIT_COMMAND).isVisible =
            count == 1 && !hasSealed
        menu.findItem(R.id.EDIT_COMMAND).isVisible = count == 1 && !hasSealed
    }

    private fun isForeignExchangeTransfer(position: Int): Boolean {
        if (_templatesCursor != null && templatesCursor.moveToPosition(position)) {
            if (!templatesCursor.isNull(KEY_TRANSFER_ACCOUNT)) {
                return templatesCursor.getString(KEY_CURRENCY) != repository.getCurrencyForAccount(
                    templatesCursor.getLong(KEY_TRANSFER_ACCOUNT)
                )
            }
        }
        return false
    }

    private fun isSealed(position: Int): Boolean {
        return if (_templatesCursor != null && templatesCursor.moveToPosition(position)) {
            templatesCursor.getInt(KEY_SEALED) == 1
        } else false
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.templates, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return handleSortOption(item)
    }

    public override fun loadData() {
        Utils.requireLoader(mManager, SORTABLE_CURSOR, null, this)
    }

    companion object {
        private const val SORTABLE_CURSOR = -1
    }
}
