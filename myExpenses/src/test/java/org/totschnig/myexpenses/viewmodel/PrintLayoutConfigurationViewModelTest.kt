package org.totschnig.myexpenses.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.printLayout
import org.totschnig.myexpenses.preference.printLayoutColumnWidths
import org.totschnig.myexpenses.preference.printLayoutDefault
import org.totschnig.myexpenses.preference.printLayoutDefaultColumnsWidths
import org.totschnig.myexpenses.viewmodel.PrintLayoutConfigurationViewModel.Companion.asColumns

/**
 * Pins the editing rules of the print layout. A layout is a flat list of fields, where ColumnFeed
 * starts the next column, and a separate list of column widths.
 */
@RunWith(AndroidJUnit4::class)
class PrintLayoutConfigurationViewModelTest : BaseViewModelTest() {

    private val notesAndReference = CombinedField(listOf(ReferenceNumber, Notes))

    private fun viewModel(
        positions: List<Position>? = null,
        widths: List<Int>? = null
    ): PrintLayoutConfigurationViewModel {
        positions?.let { prefHandler.printLayout = it }
        widths?.let { prefHandler.printLayoutColumnWidths = it }
        return PrintLayoutConfigurationViewModel(application).also {
            application.appComponent.inject(it)
            it.init()
        }
    }

    /** Date | Category | Amount with widths 100, 200, 300. */
    private fun threeColumns() = viewModel(
        listOf(Date, ColumnFeed, Category, ColumnFeed, Amount),
        listOf(100, 200, 300)
    )

    // asColumns

    @Test
    fun columnFeedSeparatesColumns() {
        assertThat(listOf(Date, Payee, ColumnFeed, Amount).asColumns())
            .containsExactly(listOf(Date, Payee), listOf(Amount)).inOrder()
    }

    @Test
    fun feedsAtTheEdgesAndInARowYieldEmptyColumns() {
        assertThat(listOf(ColumnFeed, Date, ColumnFeed, ColumnFeed).asColumns())
            .containsExactly(
                emptyList<Field>(),
                listOf(Date),
                emptyList<Field>(),
                emptyList<Field>()
            ).inOrder()
    }

    @Test
    fun emptyLayoutIsOneEmptyColumn() {
        assertThat(emptyList<Position>().asColumns()).containsExactly(emptyList<Field>())
    }

    // Loading

    @Test
    fun withoutStoredLayoutTheDefaultIsLoaded() {
        val viewModel = viewModel()
        assertThat(viewModel.positions).containsExactlyElementsIn(printLayoutDefault).inOrder()
        assertThat(viewModel.columnWidths).containsExactly(150f, 350f, 350f, 200f).inOrder()
        assertThat(viewModel.columns).hasSize(4)
    }

    @Test
    fun storedLayoutIsLoaded() {
        val viewModel = threeColumns()
        assertThat(viewModel.columns)
            .containsExactly(listOf(Date), listOf(Category), listOf(Amount)).inOrder()
        assertThat(viewModel.columnWidths).containsExactly(100f, 200f, 300f).inOrder()
    }

    @Test
    fun widthsThatDoNotMatchTheColumnsAreReset() {
        val viewModel = viewModel(listOf(Date, ColumnFeed, Amount), listOf(100, 200, 300))
        assertThat(viewModel.columnWidths).containsExactly(250f, 250f).inOrder()
    }

    @Test
    fun inactiveFieldsAreThoseNotInTheLayoutAlsoNotInACombinedField() {
        assertThat(viewModel().inactiveFields).isEmpty()
        assertThat(viewModel(listOf(Date, notesAndReference)).inactiveFields)
            .containsExactly(Category, Tags, Payee, Amount, Account, OriginalAmount)
    }

    // Moving

    @Test
    fun combineReplacesTheTargetAndRemovesTheDroppedField() = runBlocking {
        val viewModel = threeColumns()
        viewModel.combine(Date, Amount)
        assertThat(viewModel.positions)
            .containsExactly(CombinedField(listOf(Date, Amount)), ColumnFeed, Category, ColumnFeed)
            .inOrder()
    }

    @Test
    fun combineFlattensCombinedFieldsAndTakesInactiveFields() = runBlocking {
        val viewModel = viewModel(listOf(notesAndReference, ColumnFeed, Amount), listOf(1, 2))
        viewModel.combine(notesAndReference, Payee)
        assertThat(viewModel.positions.first())
            .isEqualTo(CombinedField(listOf(ReferenceNumber, Notes, Payee)))
        assertThat(viewModel.positions).hasSize(3)
    }

    @Test
    fun combineWithItselfDoesNothing() = runBlocking {
        val viewModel = threeColumns()
        viewModel.combine(Date, Date)
        assertThat(viewModel.positions)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
    }

    @Test
    fun moveNextToItselfDoesNothing() = runBlocking {
        val viewModel = threeColumns()
        viewModel.move(Category, 2)
        viewModel.move(Category, 3)
        assertThat(viewModel.positions)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
        assertThat(viewModel.allowDrop(Category, 2)).isFalse()
        assertThat(viewModel.allowDrop(Category, 3)).isFalse()
        assertThat(viewModel.allowDrop(Category, 1)).isTrue()
        assertThat(viewModel.allowDrop(Category, 4)).isTrue()
    }

    @Test
    fun moveForwardAccountsForTheRemovedField() = runBlocking {
        val viewModel = threeColumns()
        viewModel.move(Date, 4)
        assertThat(viewModel.positions)
            .containsExactly(ColumnFeed, Category, ColumnFeed, Date, Amount).inOrder()
    }

    @Test
    fun moveBackward() = runBlocking {
        val viewModel = threeColumns()
        viewModel.move(Amount, 0)
        assertThat(viewModel.positions)
            .containsExactly(Amount, Date, ColumnFeed, Category, ColumnFeed).inOrder()
    }

    @Test
    fun moveInsertsAnInactiveField() = runBlocking {
        val viewModel = threeColumns()
        viewModel.move(Notes, 1)
        assertThat(viewModel.positions)
            .containsExactly(Date, Notes, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
        assertThat(viewModel.allowDrop(Payee, 0)).isTrue()
    }

    @Test
    fun moveUpAndDownSwapWithTheNeighbour() {
        val viewModel = viewModel(listOf(Date, Payee, ColumnFeed, Amount), listOf(1, 2))
        viewModel.moveUp(Payee)
        assertThat(viewModel.positions.take(2)).containsExactly(Payee, Date).inOrder()
        viewModel.moveDown(Payee)
        assertThat(viewModel.positions.take(2)).containsExactly(Date, Payee).inOrder()
    }

    @Test
    fun moveUpOfTheFirstAndDownOfTheLastDoNothing() {
        val viewModel = threeColumns()
        viewModel.moveUp(Date)
        viewModel.moveDown(Amount)
        assertThat(viewModel.positions)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
    }

    @Test
    fun moveUpAndDownCrossColumnsWhenTheNeighbourIsAFeed() {
        val viewModel = threeColumns()
        viewModel.moveDown(Date)
        assertThat(viewModel.positions)
            .containsExactly(ColumnFeed, Date, Category, ColumnFeed, Amount).inOrder()
        viewModel.moveUp(Amount)
        assertThat(viewModel.positions)
            .containsExactly(ColumnFeed, Date, Category, Amount, ColumnFeed).inOrder()
    }

    @Test
    fun moveRightGoesToTheStartOfTheNextColumn() {
        val viewModel = threeColumns()
        viewModel.moveRight(Date)
        assertThat(viewModel.positions)
            .containsExactly(ColumnFeed, Date, Category, ColumnFeed, Amount).inOrder()
    }

    @Test
    fun moveLeftGoesToTheEndOfThePreviousColumn() {
        val viewModel = threeColumns()
        viewModel.moveLeft(Amount)
        assertThat(viewModel.positions)
            .containsExactly(Date, ColumnFeed, Category, Amount, ColumnFeed).inOrder()
    }

    @Test
    fun moveRightInTheLastAndLeftInTheFirstColumnDoNothing() {
        val viewModel = threeColumns()
        viewModel.moveRight(Amount)
        viewModel.moveLeft(Date)
        assertThat(viewModel.positions)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
    }

    @Test
    fun removeFieldTakesACombinedFieldAsAWholeButNotItsParts() {
        val viewModel = viewModel(listOf(Date, notesAndReference))
        viewModel.removeField(Notes)
        assertThat(viewModel.positions).containsExactly(Date, notesAndReference).inOrder()
        viewModel.removeField(notesAndReference)
        assertThat(viewModel.positions).containsExactly(Date)
        assertThat(viewModel.inactiveFields).containsAtLeast(Notes, ReferenceNumber)
    }

    @Test
    fun addFieldGoesToTheStartOrTheEndOfTheLayout() {
        val viewModel = viewModel(listOf(Date, ColumnFeed, Amount), listOf(1, 2))
        viewModel.addFieldStart(Notes)
        viewModel.addFieldEnd(Payee)
        assertThat(viewModel.positions)
            .containsExactly(Notes, Date, ColumnFeed, Amount, Payee).inOrder()
    }

    // Columns

    @Test
    fun addColumnInsertsAnEmptyColumnWithTheAverageWidth() {
        val viewModel = threeColumns()
        viewModel.addColumn(1)
        assertThat(viewModel.columns).containsExactly(
            listOf(Date),
            emptyList<Field>(),
            listOf(Category),
            listOf(Amount)
        ).inOrder()
        assertThat(viewModel.columnWidths).containsExactly(100f, 200f, 200f, 300f).inOrder()
    }

    @Test
    fun addColumnAtTheEdges() {
        val viewModel = threeColumns()
        viewModel.addColumn(0)
        assertThat(viewModel.positions.first()).isEqualTo(ColumnFeed)
        viewModel.addColumn(viewModel.columns.size)
        assertThat(viewModel.positions.last()).isEqualTo(ColumnFeed)
        assertThat(viewModel.columns.map { it.size }).containsExactly(0, 1, 1, 1, 0).inOrder()
        assertThat(viewModel.columnWidths).hasSize(5)
    }

    @Test
    fun removeColumnMergesItsFieldsIntoThePreviousColumnAndDropsItsWidth() {
        val viewModel = threeColumns()
        viewModel.removeColumn(1)
        assertThat(viewModel.columns)
            .containsExactly(listOf(Date, Category), listOf(Amount)).inOrder()
        assertThat(viewModel.columnWidths).containsExactly(100f, 300f).inOrder()
    }

    @Test
    fun removeFirstColumnMergesItIntoTheNextAndDropsItsWidth() {
        val viewModel = threeColumns()
        viewModel.removeColumn(0)
        assertThat(viewModel.columns)
            .containsExactly(listOf(Date, Category), listOf(Amount)).inOrder()
        assertThat(viewModel.columnWidths).containsExactly(200f, 300f).inOrder()
    }

    @Test
    fun resizeInfoIsMinCurrentAndMaxAsShareOfTheTotal() {
        val (min, current, max) = threeColumns().resizeColumnInfo(0)!!
        assertThat(min).isEqualTo(MIN_WIDTH)
        assertThat(current).isWithin(0.0001f).of(100f / 600f)
        assertThat(max).isWithin(0.0001f).of(100f / 600f + 200f / 600f - MIN_WIDTH)
    }

    @Test
    fun theLastColumnCannotBeResized() {
        assertThat(threeColumns().resizeColumnInfo(2)).isNull()
    }

    @Test
    fun resizeByPercentSetsTheShareAndTakesItFromTheNextColumn() {
        val viewModel = threeColumns()
        assertThat(viewModel.resizeColumnByPercent(0, 0.25f)).isTrue()
        assertThat(viewModel.columnWidths).containsExactly(150f, 150f, 300f).inOrder()
    }

    @Test
    fun resizeByPercentBelowTheMinimumIsRefusedButStillApplied() {
        val viewModel = threeColumns()
        assertThat(viewModel.resizeColumnByPercent(0, 0.01f)).isFalse()
        // Looks like a bug: the new widths are written before they are checked, so a refused
        // resize still changes the layout. resizeColumnByDelta does not do this.
        assertThat(viewModel.columnWidths).containsExactly(6f, 294f, 300f).inOrder()
    }

    @Test
    fun resizeByDeltaMovesWidthBetweenTwoColumns() {
        val viewModel = threeColumns()
        assertThat(viewModel.resizeColumnByDelta(1, 0.1f)).isTrue()
        assertThat(viewModel.columnWidths).containsExactly(100f, 260f, 240f).inOrder()
    }

    @Test
    fun resizeByDeltaBelowTheMinimumIsRefusedAndChangesNothing() {
        val viewModel = threeColumns()
        assertThat(viewModel.resizeColumnByDelta(0, -0.15f)).isFalse()
        assertThat(viewModel.resizeColumnByDelta(0, 0.3f)).isFalse()
        assertThat(viewModel.columnWidths).containsExactly(100f, 200f, 300f).inOrder()
    }

    // Saving

    @Test
    fun saveStoresPositionsAndWidths() {
        val viewModel = threeColumns()
        viewModel.resizeColumnByDelta(0, 0.1f)
        assertThat(viewModel.save()).isTrue()
        assertThat(prefHandler.printLayout)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
        assertThat(prefHandler.printLayoutColumnWidths).containsExactly(160, 140, 300).inOrder()
    }

    @Test
    fun saveTruncatesWidthsToIntegers() {
        val viewModel = viewModel(listOf(Date, ColumnFeed, Amount), listOf(100, 200))
        viewModel.columnWidths[0] = 99.9f
        viewModel.save()
        assertThat(prefHandler.printLayoutColumnWidths).containsExactly(99, 200).inOrder()
    }

    @Test
    fun saveDropsEmptyColumnsAtAnyPositionWithTheirWidths() {
        val viewModel = viewModel(
            listOf(ColumnFeed, Date, ColumnFeed, ColumnFeed, Amount, ColumnFeed),
            listOf(1, 2, 3, 4, 5)
        )
        assertThat(viewModel.save()).isTrue()
        assertThat(prefHandler.printLayout).containsExactly(Date, ColumnFeed, Amount).inOrder()
        assertThat(prefHandler.printLayoutColumnWidths).containsExactly(2, 4).inOrder()
    }

    @Test
    fun saveRefusesALayoutWithoutFieldsAndKeepsTheStoredOne() {
        val viewModel = threeColumns()
        listOf(Date, Category, Amount).forEach(viewModel::removeField)
        assertThat(viewModel.save()).isFalse()
        assertThat(prefHandler.printLayout)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
        assertThat(prefHandler.printLayoutColumnWidths).containsExactly(100, 200, 300).inOrder()
    }

    @Test
    fun editsAreNotStoredWithoutSave() {
        val viewModel = threeColumns()
        viewModel.removeField(Date)
        viewModel.resetToDefault()
        assertThat(prefHandler.printLayout)
            .containsExactly(Date, ColumnFeed, Category, ColumnFeed, Amount).inOrder()
    }

    @Test
    fun resetToDefaultRestoresLayoutAndWidthsAndSaveStoresThem() {
        val viewModel = threeColumns()
        viewModel.resetToDefault()
        assertThat(viewModel.positions).containsExactlyElementsIn(printLayoutDefault).inOrder()
        assertThat(viewModel.columnWidths)
            .containsExactlyElementsIn(printLayoutDefaultColumnsWidths.map { it.toFloat() })
            .inOrder()
        assertThat(viewModel.save()).isTrue()
        assertThat(prefHandler.getString(PrefKey.PRINT_LAYOUT_COLUMN_WIDTH, null))
            .isEqualTo("150,350,350,200")
    }
}
