package com.troikoss.continuum_explorer.ui.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class HelpTopicItem(
    val label: String,
    val detail: String
)

data class HelpGuideSection(
    val id: String,
    val category: String,
    val title: String,
    val badge: String,
    val icon: ImageVector,
    val items: List<HelpTopicItem>
)

/**
 * Concise Material UI Help & Open-Source Reference Center.
 * Uses short single-line headings, zero multi-line subheadings, a single-line M3 OutlinedTextField search bar,
 * and scannable reference items updated for every codebase modification.
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Server", "Desktop & UI", "Media", "Studios", "Converter", "Open Source")
    }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "unified_server_and_syncplay",
                category = "Server",
                title = "FTP Mode vs. HTTP Website Host",
                badge = "Server Hub",
                icon = Icons.Default.Sync,
                items = listOf(
                    HelpTopicItem("Independent Service Switches", "FTP (:2121), HTTP Website Host (:8080), SyncPlay Party, and Screen Mirroring each have independent switches — never activated all at once."),
                    HelpTopicItem("Mode 1: FTP, SyncPlay & Cast", "Host FTP with Upload Dropzone Only (0 host files exposed), Selected Items, or Full Storage, plus optional Screen Share or SyncPlay."),
                    HelpTopicItem("Mode 2: HTTP Website Host", "Host an unzipped website directory (HTML/CSS/JS/assets) or a single HTML file as a live web server with in-app WebView preview."),
                    HelpTopicItem("SyncPlay Party & Mandatory PIN", "Host-controlled watch/listen space with mandatory 6-digit Room Passcode, smooth ±3% playbackRate drift micro-nudging (zero periodic freeze), and auto history wipe on stop."),
                    HelpTopicItem("Network Firewall & Sandbox", "Canonical path traversal protection, executable upload blocker, Untrusted Wi-Fi PIN gate, and isolated ContinuumSuite/MiniAppsSandbox workspace.")
                )
            ),
            HelpGuideSection(
                id = "desktop_and_ui",
                category = "Desktop & UI",
                title = "Android Desktop/DeX & Gallery",
                badge = "DeX + M3",
                icon = Icons.Default.DesktopWindows,
                items = listOf(
                    HelpTopicItem("Default Window Mode (Option 2)", "Animated In-App Tabs across DeX, Tablet & Mobile is set as the default workspace mode."),
                    HelpTopicItem("Split-Pane Desktop File Pickers", "File selection dialogs and full-page pickers render a Left Places Sidebar + Multi-Column Compact Tiles on Desktop/DeX."),
                    HelpTopicItem("My Suite Desktop Grid", "Multi-column Studio and Recent File cards on Desktop/DeX; narrow windows (<560dp) collapse New File & Open into icon-only header buttons."),
                    HelpTopicItem("Lockable Top Heading Bars", "All subpages include a Pin/Lock button to keep the 50dp heading bar frozen on top while scrolling."),
                    HelpTopicItem("Instant Gallery & Adaptive Grid", "Zero-syscall indexed MediaStore query with warm cache + full-bleed adaptive grid tiles without gaps.")
                )
            ),
            HelpGuideSection(
                id = "video_and_music_players",
                category = "Media",
                title = "Media & 3D Players",
                badge = "Media3",
                icon = Icons.Default.OndemandVideo,
                items = listOf(
                    HelpTopicItem("3D Video Modes", "Normal 2D, Red-Cyan Anaglyph, Red-Green 3D (Dubois matrix) & Side-by-Side VR."),
                    HelpTopicItem("Tracks & Speed", "Switch multi-language audio tracks, subtitles/CC, and playback speed (0.25x–3.0x)."),
                    HelpTopicItem("Swipe Gestures", "Left vertical = Brightness, Right vertical = Volume, Horizontal = Scrub."),
                    HelpTopicItem("Music Player", "Full Studio Deck, Floating Pop-Up Mini-Window, or Minimized Pill with 5-Band EQ.")
                )
            ),
            HelpGuideSection(
                id = "studios_and_html",
                category = "Studios",
                title = "Studios & HTML Preview",
                badge = "Studios",
                icon = Icons.Default.Html,
                items = listOf(
                    HelpTopicItem("HTML5 Previewer", "Rendered WebView + Raw HTML source inspector with DOM Console Overlay."),
                    HelpTopicItem("Markdown Studio", "Markor engine (`TextStudioEngine.kt`) for Markdown, Todo.txt, Org & AsciiDoc."),
                    HelpTopicItem("Code & Table Studios", "Syntax code editor with line numbers and interactive CSV/TSV cell grid."),
                    HelpTopicItem("Photo Studio", "Non-destructive filters, crop, brush, shapes, text & stickers (`ImageStudioCanvasView.kt`).")
                )
            ),
            HelpGuideSection(
                id = "converter_and_security",
                category = "Converter",
                title = "File Converter",
                badge = "VERT",
                icon = Icons.Default.Transform,
                items = listOf(
                    HelpTopicItem("Exact-KB Resizer", "Binary-search JPEG/WebP quality & DPI solver for 20KB/50KB exam uploads."),
                    HelpTopicItem("PDF Compressor", "Grayscale & DPI rasterization to shrink scanned PDFs offline."),
                    HelpTopicItem("Batch Converter", "Convert images, audio/video, documents, data (JSON/YAML/XML/CSV) & ZIP archives.")
                )
            ),
            HelpGuideSection(
                id = "open_source_knowledge",
                category = "Open Source",
                title = "Open-Source Credits",
                badge = "FOSS",
                icon = Icons.Default.Code,
                items = listOf(
                    HelpTopicItem("Continuum Explorer (GPLv3)", "Core multi-pane file explorer architecture by troikoss."),
                    HelpTopicItem("Markor (Apache 2.0)", "Markdown, Todo.txt, Org-Mode & AsciiDoc text engine by Gregor Santner."),
                    HelpTopicItem("PhotoEditor (MIT)", "Canvas brush, filter matrix & sticker layers by Burhanuddin Rashid."),
                    HelpTopicItem("Google Fonts (SIL OFL 1.1)", "Bundled Plus Jakarta Sans & JetBrains Mono offline typography."),
                    HelpTopicItem("Ktor & Apache FtpServer (Apache 2.0)", "Embedded HTTP Website/SyncPlay server & RFC-959 FTP daemon."),
                    HelpTopicItem("AndroidX Media3 & Compose M3 (Apache 2.0)", "Hardware media engine, Adaptive Grid & Material UI theme system."),
                    HelpTopicItem("PDFBox, POI, Commons Compress & ZXing", "Offline PDF, Office, archive & QR engines (Apache 2.0).")
                )
            )
        )
    }

    val filteredSections = remember(searchFilter, selectedCategory, sections) {
        val q = searchFilter.trim().lowercase()
        sections.filter { sec ->
            val matchesCat = selectedCategory == "All" || sec.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                sec.title.lowercase().contains(q) ||
                sec.badge.lowercase().contains(q) ||
                sec.items.any { it.label.lowercase().contains(q) || it.detail.lowercase().contains(q) }
            matchesCat && matchesQuery
        }
    }

    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            sections.forEach { put(it.id, true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchFilter,
            onValueChange = { searchFilter = it },
            placeholder = {
                Text(
                    text = "Search help...",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search Help",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchFilter.isNotEmpty()) {
                    IconButton(onClick = { searchFilter = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("help_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            val allExpanded = filteredSections.all { expandedStates[it.id] == true }
            AssistChip(
                onClick = {
                    val target = !allExpanded
                    filteredSections.forEach { expandedStates[it.id] = target }
                },
                label = {
                    Text(
                        if (allExpanded) "Collapse" else "Expand",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )
        }

        filteredSections.forEach { sec ->
            val isExpanded = expandedStates[sec.id] ?: true
            val q = searchFilter.trim().lowercase()
            val visibleItems = if (q.isEmpty()) {
                sec.items
            } else {
                sec.items.filter {
                    it.label.lowercase().contains(q) ||
                        it.detail.lowercase().contains(q) ||
                        sec.title.lowercase().contains(q)
                }
            }

            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedStates[sec.id] = !isExpanded }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = sec.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = sec.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = sec.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            visibleItems.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
