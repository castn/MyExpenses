package org.totschnig.myexpenses.activity

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.provider.KEY_USAGES
import org.totschnig.myexpenses.provider.TransactionProvider

@RunWith(AndroidJUnit4::class)
class TemplateSaverTest : BaseTestWithRepository() {

    private var templateId: Long = 0

    @Before
    fun setUp() {
        Robolectric.buildContentProvider(TransactionProvider::class.java).create()
        templateId = insertTemplate(insertAccount("Test Account"), "Template", 100)
    }

    private fun transactions() = contentResolver
        .query(TransactionProvider.TRANSACTIONS_URI, arrayOf(KEY_ROWID), null, null, null)!!
        .use { it.count }

    private fun launch(configure: Intent.() -> Unit) = ActivityScenario.launch<TemplateSaver>(
        Intent(application, TemplateSaver::class.java).apply(configure)
    ).also { shadowOf(Looper.getMainLooper()).idle() }

    @Test
    fun isNotOpenToOtherApps() {
        val component = ComponentName(application, TemplateSaver::class.java)

        assertThat(application.packageManager.getActivityInfo(component, 0).exported).isFalse()
    }

    @Test
    fun savesTransactionFromTemplate() {
        launch { putExtra(KEY_TEMPLATEID, templateId) }

        assertThat(transactions()).isEqualTo(1)
    }

    @Test
    fun countsTheUseOfTheTemplate() {
        launch { putExtra(KEY_TEMPLATEID, templateId) }

        val usages = contentResolver.query(
            TransactionProvider.TEMPLATES_URI,
            arrayOf(KEY_USAGES),
            "$KEY_ROWID = ?",
            arrayOf(templateId.toString()),
            null
        )!!.use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertThat(usages).isEqualTo(1)
    }

    @Test
    fun ignoresIntentWithoutTemplate() {
        val scenario = launch { }

        assertThat(scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
        assertThat(transactions()).isEqualTo(0)
    }

    @Test
    fun ignoresInvalidTemplateId() {
        val scenario = launch { putExtra(KEY_TEMPLATEID, -1L) }

        assertThat(scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
        assertThat(transactions()).isEqualTo(0)
    }

    @Test
    fun savesNothingForUnknownTemplate() {
        launch { putExtra(KEY_TEMPLATEID, templateId + 1) }

        assertThat(transactions()).isEqualTo(0)
    }
}
