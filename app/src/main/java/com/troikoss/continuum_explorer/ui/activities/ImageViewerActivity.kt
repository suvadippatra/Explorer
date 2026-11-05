package com.troikoss.continuum_explorer.ui.activities

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File

class ImageViewerActivity : FullscreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.data?.toString() ?: intent.getStringExtra("IMAGE_URI")

        setContent {
            FileExplorerTheme {
                ImageViewerScreen(
                    initialImageUri = imageUri
                )
            }
        }
    }
}

@Composable
fun ImageViewerScreen(
    initialImageUri: String?
) {
    val activity = (LocalView.current.context as? Activity)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val dummyAppState = remember { FileExplorerState(context, coroutineScope) }

    val universalFile = remember(initialImageUri) {
        if (initialImageUri != null) {
            val u = Uri.parse(initialImageUri)
            if (u.scheme == "file" || u.scheme == null) {
                File(u.path ?: initialImageUri).toUniversal()
            } else {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, u)
                doc?.toUniversal()
            }
        } else {
            null
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            if (universalFile != null) {
                com.troikoss.continuum_explorer.ui.editor.NativeImageViewerEditor(
                    file = universalFile,
                    appState = dummyAppState,
                    onMenuClick = { activity?.finish() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No image selected", color = Color.White)
                }
            }
        }
    }
}
