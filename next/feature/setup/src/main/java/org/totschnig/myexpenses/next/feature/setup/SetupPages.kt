package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.IntroBottomBar
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberPercentFormat

/** What the categories step brought: the share of recent debits categorized without asking */
@Composable
internal fun SetupAutomaticPage(viewModel: SetupViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val share by viewModel.automaticShare.collectAsStateWithLifecycle()
    val percent = rememberPercentFormat()
    IntroColumn(onBack) {
        IntroPage(
            title = stringResource(R.string.next_setup_automatic_title),
            text = share?.let {
                stringResource(R.string.next_setup_automatic_text, percent(it.share))
            },
            modifier = Modifier.weight(1f),
            hero = { HeroIllustration(DesignR.drawable.illustration_done) }
        ) {
            Text(
                stringResource(R.string.next_setup_automatic_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IntroBottomBar(nextLabel = stringResource(R.string.next_setup_next), onNext = onNext)
    }
}

/**
 * The contracts step around the suggestions of contracts the contracts area hands in
 *
 * @param onEnd with whether the step was skipped
 */
@Composable
internal fun SetupContractsPage(
    onBack: () -> Unit,
    onEnd: (skipped: Boolean) -> Unit,
    contracts: (@Composable ColumnScope.() -> Unit)?
) {
    Column(Modifier.fillMaxSize()) {
        DetailScaffold(
            onBack = onBack,
            title = stringResource(R.string.next_setup_step_contracts),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                stringResource(R.string.next_setup_contracts_text),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = Spacing.content)
            )
            contracts?.invoke(this)
        }
        IntroBottomBar(
            nextLabel = stringResource(R.string.next_setup_finish_step),
            onNext = { onEnd(false) },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            skipLabel = stringResource(R.string.next_setup_skip_step),
            onSkip = { onEnd(true) }
        )
    }
}

/** The end of the setup, back to the overview */
@Composable
internal fun SetupDonePage(onClose: () -> Unit) {
    IntroColumn(onBack = onClose) {
        IntroPage(
            title = stringResource(R.string.next_setup_done_title),
            text = stringResource(R.string.next_setup_done_text),
            modifier = Modifier.weight(1f),
            hero = { HeroIllustration(DesignR.drawable.illustration_done) }
        )
        IntroBottomBar(
            nextLabel = stringResource(R.string.next_setup_to_overview),
            onNext = onClose
        )
    }
}

/** A page like those of the first start, with a way back at the top */
@Composable
internal fun IntroColumn(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        PageTopBar(onBack = onBack)
        content()
    }
}
