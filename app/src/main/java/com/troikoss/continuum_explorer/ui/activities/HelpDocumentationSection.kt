package com.troikoss.continuum_explorer.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HelpGuideSection(
    val id: String,
    val title: String,
    val badge: String,
    val icon: ImageVector,
    val summary: String,
    val bullets: List<String>
)

/**
 * Comprehensive In-App Help & Documentation Page.
 * Updated with every code modification from the Argon Audit overhaul.
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "ui_hierarchy",
                title = "1. UI Structure: Title Bar & Flush Heading",
                badge = "UI Standard",
                icon = Icons.Default.ViewQuilt,
                summary = "Continuum Explorer strictly follows a unified two-tier top bar architecture across all file browsing, previewing, and editing screens.",
                bullets = listOf(
                    "Title Bar (Tab Bar): Fixed at the very top of the window for instant single-tap tab switching without redundant scroll lag.",
                    "Heading (Action Bar): Positioned directly and flush underneath the Title Bar with zero middle gaps, containing navigation breadcrumbs, format actions, and contextual controls.",
                    "Adaptive Split Panes: On tablets, foldables, and desktops (>= 600dp), the workspace automatically supports a resizable Left Navigation Pane and Right-Hand Split Preview / Details Pane."
                )
            ),
            HelpGuideSection(
                id = "performance_60fps",
                title = "2. Cold-Start & 60fps Scrolling Optimization",
                badge = "Argon Audit",
                icon = Icons.Default.Speed,
                summary = "Background I/O deferral and grid composition optimizations eliminate startup delays and frame drops.",
                bullets = listOf(
                    "Non-Blocking Cold Start: Storage volume capacity queries (Internal Storage & Removable SD/USB drives) run asynchronously on Dispatchers.IO so the app and navigation drawer open immediately.",
                    "60fps File Grid & Details View: LazyVerticalGrid uses stable providerId keys, a single grid-level focus observer instead of per-item coroutines, and background I/O directory item counting.",
                    "Instant Tab Switching: TabBar only scrolls when a newly selected tab lies outside the visible viewport bounds."
                )
            ),
            HelpGuideSection(
                id = "markor_editor",
                title = "3. Markor Universal Text, Markdown, Todo.txt & CSV Editor",
                badge = "Markor Engine",
                icon = Icons.Default.EditNote,
                summary = "Powered by the Markor format architecture with cached syntax highlighting, smart list continuation, and local WebView rendering.",
                bullets = listOf(
                    "Live WebView Preview (file:///android_asset/): Renders Markdown with offline KaTeX math equations ($...$ and $$...$$), Prism.js code block highlighting, and Mermaid.js diagrams (```mermaid).",
                    "Interactive Todo.txt Task Board: Toggle between Raw Text and the interactive Task Board with one-tap task completion (x YYYY-MM-DD), priority cycling (A)-(C), and @context / +project filter chips.",
                    "Interactive CSV Visual Grid: Switch between raw CSV text (with column alignment) and the Visual Spreadsheet Grid to add/delete rows and columns.",
                    "Org-Mode & AsciiDoc Support: Native WebView previewing and syntax highlighting for .org and .adoc documents, plus Table of Contents (TOC) outliner drawer and smart list auto-continuation on Enter."
                )
            ),
            HelpGuideSection(
                id = "file_previewers",
                title = "4. Modular File Previewers (Video, Audio, PDF, Archive & Image)",
                badge = "Preview Engine",
                icon = Icons.Default.Preview,
                summary = "Monolithic viewer code has been split into dedicated, lifecycle-aware preview engines under ui.preview and ui.editor.pdf.",
                bullets = listOf(
                    "Video & Audio ExoPlayer Previewers: Lifecycle-aware playback (auto-pauses on background), zero black-screen surface rebinding, Fit/Crop/Stretch aspect ratio toggle, 0.5x–2.0x speed selector, double-tap 10s seek, and ID3/resolution metadata.",
                    "PDF Viewer & Booklet Reader: Multi-threaded PdfRenderer tile cache with crisp white page backgrounds, smooth 1-finger scrolling + 2-finger pinch zoom, toolbar zoom controls (- / 100% / +), Two-Page Booklet Spread, thumbnail scrub bar, and ML Kit OCR text search.",
                    "Interactive Archive Browser & Extractor: Inspect .zip, .jar, .apk, .tar, .gz, .7z, and .rar archives directly, filter entries, open archives as a native Explorer tab, or extract to a dedicated folder.",
                    "Right-Hand Split Preview Pane: Selecting or hovering a file on tablet/desktop shows live Page 1 PDF bitmaps, Archive entry summaries, Image/Video previews, and syntax snippets."
                )
            ),
            HelpGuideSection(
                id = "file_picker_network",
                title = "5. Multi-Select Bucket Picker, Network & Shortcuts",
                badge = "Workspace",
                icon = Icons.Default.FolderSpecial,
                summary = "Built-in file operations, network storage protocols, and hardware keyboard shortcuts.",
                bullets = listOf(
                    "FullPageFilePicker Bucket: Queue multiple files and folders across different directories before confirming batch copy, move, or compression.",
                    "FTP & HTTP Web Server Hub: Share device storage over local Wi-Fi or connect to remote FTP, SFTP, SMB, and WebDAV servers.",
                    "Keyboard Shortcuts: Ctrl+C (Copy), Ctrl+X (Cut), Ctrl+V (Paste), Ctrl+A (Select All), F2 (Inline Rename), Del (Delete/Recycle Bin), Ctrl+F (Search).",
                    "GitHub Push & Clean Workspace: The standalone reference demo repositories (markor-master, Continuum-Explorer-master, and PhotoEditor-master) are safely excluded from Git commits via .gitignore, ensuring browser pushes to GitHub remain clean and within GitHub API payload limits while retaining all reference code on disk."
                )
            )
        )
    }

    val filteredSections = remember(sections, searchFilter) {
        if (searchFilter.isBlank()) sections
        else sections.filter { sec ->
            sec.title.contains(searchFilter, ignoreCase = true) ||
                sec.summary.contains(searchFilter, ignoreCase = true) ||
                sec.bullets.any { it.contains(searchFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Continuum Explorer Help & Argon Audit Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Updated for all Argon Audit UI, Markor Editor, Previewer & 60fps Architecture modules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search help topics (e.g. Markor, KaTeX, Todo.txt, PDF, Archive, 60fps)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchFilter.isNotEmpty()) {
                            IconButton(onClick = { searchFilter = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        filteredSections.forEach { section ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                section.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = section.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = section.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        section.bullets.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
