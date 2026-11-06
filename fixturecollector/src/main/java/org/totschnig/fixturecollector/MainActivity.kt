package org.totschnig.fixturecollector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import kotlinx.coroutines.launch
import org.totschnig.mlkitcore.documentScanResult
import org.totschnig.mlkitcore.documentScannerOptions
import org.totschnig.myexpenses.designsystem.theme.DesignSystemTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CollectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DesignSystemTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    App(viewModel)
                }
            }
        }
    }
}

@Composable
private fun App(viewModel: CollectorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fixtures by viewModel.fixtures.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val scanner =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                documentScanResult(result.data)?.let(viewModel::newPhoto)
            }
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::newPhoto)
        }

    message?.let {
        val text = context.getString(it.text, it.arg)
        LaunchedEffect(it) {
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    val current = draft
    if (current == null) {
        FixtureListScreen(
            fixtures = fixtures,
            onOpen = viewModel::open,
            onScan = {
                GmsDocumentScanning.getClient(documentScannerOptions())
                    .getStartScanIntent(context as Activity)
                    .addOnSuccessListener {
                        scanner.launch(IntentSenderRequest.Builder(it).build())
                    }
                    .addOnFailureListener {
                        viewModel.showMessage(Message(R.string.error_scanner, it.message))
                    }
            },
            onGallery = {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onShare = {
                scope.launch {
                    val zip = viewModel.zip()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zip
                    )
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.share_chooser))
                    )
                }
            }
        )
    } else {
        FixtureEditScreen(draft = current, viewModel = viewModel)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar)
    }
}
