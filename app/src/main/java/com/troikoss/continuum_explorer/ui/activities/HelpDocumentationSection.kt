package com.troikoss.continuum_explorer.ui.activities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Updated with every code modification from the Argon Audit & Desktop/Server Overhaul.
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "window_fullscreen_layout",
                title = "1. Edge-to-Edge Window, Fullscreen Toggle & Layout Switcher",
                badge = "UI & Window",
                icon = Icons.Default.Fullscreen,
                summary = "Zero-clipping edge-to-edge layouts across tablet/desktop windows with one-tap Fullscreen and instant View Mode switching.",
                bullets = listOf(
                    "Edge-to-Edge Window Fix: Eliminated outer horizontal margins that clipped file names and column headers in freeform tablet and multi-window views.",
                    "Full-Screen Mode Toggle: Tap the Fullscreen icon in the TopBar, Separate-Tab File Preview, My Suite, FTP/HTTP Server, Markor Text Editor, or Photo Editor to hide the top Tab Bar and maximize workspace height.",
                    "Restored Layout & Column Count Switcher: Use the quick View Mode button in the TopBar to switch between Details, List, Grid, and Gallery views, adjust the Grid Column Count slider (2–6 columns), and scale icon sizes (60%–200%).",
                    "Sidebar Storage Capacity Display: Compact single-line formatting ensures 'XX GB free of XX GB' never wraps or breaks in narrow navigation drawers."
                )
            ),
            HelpGuideSection(
                id = "my_suite_and_preview",
                title = "2. Redesigned 'My Suite' & Separate-Tab File Preview Bar",
                badge = "Workspace",
                icon = Icons.Default.AutoAwesomeMosaic,
                summary = "Minimalist creative suite for authoring new files by extension and logically ordered actions in separate-tab file previews.",
                bullets = listOf(
                    "Create New File by Extension in My Suite: Type any filename and extension (e.g. notes.md, tasks.todo.txt, data.csv, script.kt, page.html) to automatically open the matching Markor editor layout (Markdown, Todo.txt Task Board, CSV Spreadsheet Grid, Org-Mode, AsciiDoc, or Code Editor).",
                    "Open Editable Files & Guard Unsupported Media: Open existing text, code, PDF, or image files directly from My Suite while non-editable media formats like video are clearly blocked from the editor selector.",
                    "Separate-Tab File Preview Logical Action Bar: Displays File Name + Format/Size subtitle on the left, followed in logical order by Edit, Metadata View/Edit (inline rename & extension change, EXIF camera/artist/copyright editor, GPS coordinate stripper, and MD5/SHA-256 checksums), Delete, Share, Open External, and Fullscreen toggle.",
                    "Desktop Right-Sidebar PhotoEditor & Markor Ergonomics: On wide/tablet screens (>= 600dp), PhotoEditor docks its tool palette and adjustment sliders on the right sidebar so the image canvas is never obscured, while mobile keeps the bottom toolbar."
                )
            ),
            HelpGuideSection(
                id = "ftp_website_server",
                title = "3. FTP/HTTP File Server & Self-Hosted Website Hub",
                badge = "Server Hub",
                icon = Icons.Default.Dns,
                summary = "Minimalist Segmented Mode Toggle switching between FTP/HTTP File Sharing and Self-Hosted Website hosting, plus a 3-layout Web Portal.",
                bullets = listOf(
                    "Mode 1 — FTP & File Share: Share either a Selected Files & Folders Bucket (powered by instant OS symlinks) or Full Device Storage over FTP (default port 2121) and the HTTP Web Portal (port 8080).",
                    "Mode 2 — Self-Hosted Website: Pick any specific .html file or a folder containing website data (such as a static web repository downloaded from GitHub). Automatically serves index.html / entry HTML and resolves all relative CSS, JS, images, fonts, and SPA routes.",
                    "3-Layout Responsive Web Portal: Browsers connecting to port 8080 can switch between Desktop Layout (multi-column table + side-by-side live screen mirror), Mobile Layout (touch cards), and Mirror View (theatre screen mirroring with rotation and fullscreen)."
                )
            ),
            HelpGuideSection(
                id = "apk_ci_cd",
                title = "4. Release APK Compression & Multi-ABI GitHub Actions CI/CD",
                badge = "Build & CI",
                icon = Icons.Default.BuildCircle,
                summary = "Compressed JNI packaging, per-ABI native splitting, and error-free GitHub Actions compilation for all Android architectures.",
                bullets = listOf(
                    "Release APK Size Compression & R8 Fix: Compresses APK size via per-ABI native .so filtering (-PabiTarget), Deflate-compressed JNI packaging (useLegacyPackaging = true), PNG crunching, and metadata exclusions while bypassing brittle R8 OSGi/Log4j bytecode verification failures.",
                    "Multi-Architecture GitHub Workflow (.github/workflows/android-build.yml): Builds arm64-v8a, armeabi-v7a, x86_64, x86, Universal APK, and Play Store AAB packages using actions/setup-java@v5 (JDK 21), Node 24 actions compatibility, warm Gradle daemon reuse, and Gradle 9.3.1.",
                    "Automated 0.0.x+1 Versioning & Optional Releases: Base version starts at 0.0.1 and automatically increments to 0.0.x+1 on each workflow run (or accepts a custom number/version input) with optional one-click GitHub Release publishing and zero personal data exposure."
                )
            ),
            HelpGuideSection(
                id = "markor_editor",
                title = "5. Markor Universal Text, Markdown, Todo.txt & CSV Editor",
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
                title = "6. Modular File Previewers & 60fps Performance",
                badge = "Preview Engine",
                icon = Icons.Default.Preview,
                summary = "Dedicated, lifecycle-aware preview engines under ui.preview and ui.editor.pdf with non-blocking cold start.",
                bullets = listOf(
                    "Video & Audio ExoPlayer Previewers: Lifecycle-aware playback (auto-pauses on background), Fit/Crop/Stretch aspect ratio toggle, 0.5x–2.0x speed selector, double-tap 10s seek, and ID3/resolution metadata.",
                    "PDF Viewer & Booklet Reader: Multi-threaded PdfRenderer tile cache with crisp white page backgrounds, smooth 1-finger scrolling + 2-finger pinch zoom, toolbar zoom controls (- / 100% / +), Two-Page Booklet Spread, and ML Kit OCR text search.",
                    "Interactive Archive Browser & Extractor: Inspect .zip, .jar, .apk, .tar, .gz, .7z, and .rar archives directly, filter entries, open archives as a native Explorer tab, or extract to a dedicated folder.",
                    "60fps Scrolling & Instant Cold Start: Storage volume queries run asynchronously on Dispatchers.IO while LazyVerticalGrid uses stable providerId keys and single focus observers."
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
                                text = "Continuum Explorer Help & Feature Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Updated with Fullscreen Mode, My Suite, Self-Hosted Website Server, Metadata Editor & Multi-ABI CI/CD",
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
                    placeholder = { Text("Search help topics (e.g. Fullscreen, Website, FTP, My Suite, Metadata, APK)...") },
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
