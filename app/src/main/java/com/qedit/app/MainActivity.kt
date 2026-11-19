package com.qedit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.qedit.app.ui.EditorScreen
import com.qedit.app.ui.theme.QEditTheme
import com.qedit.app.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.openUri(it) }
    }

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveActiveTabAs(it) }
    }

    private val openFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.openFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            QEditTheme {
                EditorScreen(
                    viewModel = viewModel,
                    onRequestOpenFile = {
                        openFileLauncher.launch(
                            arrayOf("text/*", "application/x-yaml", "application/yaml", "application/json", "*/*")
                        )
                    },
                    onRequestSaveAs = { suggestedName ->
                        createFileLauncher.launch(suggestedName)
                    },
                    onRequestOpenFolder = {
                        openFolderLauncher.launch(null)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            intent.data?.let { uri ->
                viewModel.openUri(uri)
            }
        }
    }
}
