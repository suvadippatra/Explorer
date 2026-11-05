package com.troikoss.continuum_explorer.ui.editor

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import java.io.File

/**
 * Standard Markdown Rendering View using Markwon for rich inline & block rendering.
 */
@Composable
fun MarkdownViewer(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val markwon = remember(context) {
        Markwon.create(context)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 15f
                setTextColor(colorScheme.onSurface.toArgb())
                setLineSpacing(4f, 1.2f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(colorScheme.onSurface.toArgb())
            val formattedText = LaTeXMathHelper.formatLaTeXMath(markdownText)
            markwon.setMarkdown(textView, formattedText.ifEmpty { "*Empty document*" })
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
    )
}

/**
 * Markdown Viewer & Editor with dual preview / edit modes.
 */
@Composable
fun MarkdownViewer(
    file: File,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var markdownText by remember(file.absolutePath, content) { mutableStateOf(content) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Preview, 1 = Edit Source

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = "Markdown",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.width(160.dp)) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Preview", style = MaterialTheme.typography.labelSmall) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    IconButton(onClick = { onSave() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (selectedTab == 0) {
                // Rendered Preview using Markwon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownViewer(
                        markdownText = markdownText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Raw Source Editor
                OutlinedTextField(
                    value = markdownText,
                    onValueChange = {
                        markdownText = it
                        onContentChange(it)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
