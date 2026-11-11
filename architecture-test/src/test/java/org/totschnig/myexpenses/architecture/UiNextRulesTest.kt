package org.totschnig.myexpenses.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Rules analysis-data-class, screen-no-alert-dialog, vm-constructor, vm-entry-points and vm-io-param of
 * ui_next/ARCHITECTURE.md.
 */
class UiNextRulesTest {

    private val scope = nextScope

    @Test
    fun `analysis-data-class inputs and facts are data classes`() {
        scope.classes()
            .filter { it.name.endsWith("Input") || it.name.endsWith("Facts") }
            .filterNot { it.name in NOT_ANALYSIS_INPUTS }
            .assertTrue(
                additionalMessage =
                    "analysis-data-class: inputs and facts are data classes, so that equal data compares equal"
            ) {
                it.hasDataModifier
            }
    }

    @Test
    fun `screen-no-alert-dialog no AlertDialog in ui_next`() {
        scope.imports
            .assertFalse(
                additionalMessage = "screen-no-alert-dialog: dialogs come from the dialogs of the design system"
            ) {
                it.name.endsWith(".AlertDialog")
            }
    }

    @Test
    fun `vm-constructor view models get their dependencies through the constructor`() {
        scope.classes()
            .filter { it.isViewModel() }
            .assertTrue(additionalMessage = "vm-constructor: no field injection, no by lazy, no start()") { viewModel ->
                viewModel.properties().none { it.hasAnnotationWithName("Inject") || it.hasDelegate("lazy") } &&
                    viewModel.functions().none { it.name == "start" }
            }
    }

    @Test
    fun `vm-entry-points view models are imported only by entry points`() {
        scope.files
            .filterNot { it.isEntryPoint() }
            .flatMap { it.imports }
            .assertFalse(
                additionalMessage = "vm-entry-points: only MainActivity, NextSettings, the …Feature entries of the " +
                    "shell and the …Flow of a feature import view models"
            ) {
                VIEW_MODEL_OF_NEXT.matches(it.name)
            }
    }

    @Test
    fun `vm-io-param view models take their dispatchers as parameters`() {
        scope.classes()
            .filter { it.isViewModel() }
            .assertFalse(
                additionalMessage = "vm-io-param: take `io: CoroutineDispatcher = Dispatchers.IO` or " +
                    "`compute: CoroutineDispatcher = Dispatchers.Default` as a parameter, " +
                    "so that tests can run the view model"
            ) { viewModel ->
                val constructor = viewModel.primaryConstructor?.text.orEmpty()
                val body = viewModel.text.replace(constructor, "")
                body.contains("Dispatchers.IO") || body.contains("Dispatchers.Default")
            }
    }

    private fun KoClassDeclaration.isViewModel() =
        parents().any { it.name == "ViewModel" || it.name == "AndroidViewModel" }

    private fun KoFileDeclaration.isEntryPoint() =
        name in ENTRY_POINTS || name.endsWith("Flow") || name.endsWith("Feature")

    private companion object {
        val ENTRY_POINTS = setOf("MainActivity", "NextSettings")
        val VIEW_MODEL_OF_NEXT = Regex("""org\.totschnig\.myexpenses\.next(\.\w+)+\.\w+ViewModel""")

        /** Named like an input, but not the input of an analysis. */
        val NOT_ANALYSIS_INPUTS = setOf(
            "AmountInput", // receipts: reads what is typed into an amount field
        )
    }
}
