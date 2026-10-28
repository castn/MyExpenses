package org.totschnig.myexpenses.next.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.designsystem.ChoiceCard
import org.totschnig.myexpenses.designsystem.ChoiceOption
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R as NextR

/** How the user starts: with a new account or with data they already have */
enum class StartChoice { NEW_ACCOUNT, BACKUP_FILE, SYNC, BANK }

/** Offered together in one card, as both bring back data the user had before */
private val RESTORE_CHOICES = setOf(StartChoice.BACKUP_FILE, StartChoice.SYNC)

/**
 * @param offersBanking false where the app cannot connect to banks
 */
@Composable
internal fun StartPage(selected: StartChoice, offersBanking: Boolean, onSelect: (StartChoice) -> Unit) {
    IntroPage(
        title = stringResource(NextR.string.next_onboarding_start_title),
        text = stringResource(NextR.string.next_onboarding_start_text),
        hero = { HeroIllustration(DesignR.drawable.illustration_start) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @Composable
            fun Choice(choice: StartChoice, icon: ImageVector, title: Int, text: Int, details: (@Composable () -> Unit)? = null) =
                ChoiceCard(
                    icon = icon,
                    title = stringResource(title),
                    text = stringResource(text),
                    selected = selected == choice,
                    onClick = { onSelect(choice) },
                    details = details?.let { content -> { content() } }
                )

            Choice(
                StartChoice.NEW_ACCOUNT, Icons.Outlined.AddCircleOutline,
                NextR.string.next_onboarding_start_new, NextR.string.next_onboarding_start_new_text
            )
            ChoiceCard(
                icon = Icons.Outlined.SettingsBackupRestore,
                title = stringResource(NextR.string.next_onboarding_restore),
                text = stringResource(NextR.string.next_onboarding_restore_text),
                selected = selected in RESTORE_CHOICES,
                // Opens with the backup file picked, the user changes it to sync inside
                onClick = { if (selected !in RESTORE_CHOICES) onSelect(StartChoice.BACKUP_FILE) }
            ) {
                RestoreOptions(selected, onSelect)
            }
            if (offersBanking) {
                Choice(
                    StartChoice.BANK, Icons.Outlined.AccountBalance,
                    NextR.string.next_onboarding_restore_bank, NextR.string.next_onboarding_restore_bank_text
                ) { BankExplanation() }
            }
        }
    }
}

/** Where to restore from: a backup file or a cloud service the user syncs with */
@Composable
private fun RestoreOptions(selected: StartChoice, onSelect: (StartChoice) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceOption(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(NextR.string.next_onboarding_restore_file),
            text = stringResource(NextR.string.next_onboarding_restore_file_text),
            selected = selected == StartChoice.BACKUP_FILE,
            onClick = { onSelect(StartChoice.BACKUP_FILE) }
        )
        ChoiceOption(
            icon = Icons.Outlined.CloudSync,
            title = stringResource(NextR.string.next_onboarding_restore_sync),
            text = stringResource(NextR.string.next_onboarding_restore_sync_text),
            selected = selected == StartChoice.SYNC,
            onClick = { onSelect(StartChoice.SYNC) }
        )
    }
}

/** What loading from the bank means, for someone who never heard of FinTS */
@Composable
private fun BankExplanation() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(NextR.string.next_onboarding_bank_how),
            style = MaterialTheme.typography.bodyMedium
        )
        ExplanationRow(Icons.Outlined.Key, stringResource(NextR.string.next_onboarding_bank_credentials))
        ExplanationRow(Icons.Outlined.PhonelinkLock, stringResource(NextR.string.next_onboarding_bank_tan))
        ExplanationRow(Icons.Outlined.Lock, stringResource(NextR.string.next_onboarding_bank_direct))
    }
}

@Composable
private fun ExplanationRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun StartPagePreview() {
    NextPreviewTheme {
        StartPage(StartChoice.BANK, offersBanking = true, onSelect = {})
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun StartPageRestorePreview() {
    NextPreviewTheme {
        StartPage(StartChoice.SYNC, offersBanking = true, onSelect = {})
    }
}
