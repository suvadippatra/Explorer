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
        listOf("All", "Hosting", "Desktop & UI", "Media", "Studios", "Converter", "Open Source")
    }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "hosting_ftp_http",
                category = "Hosting",
                title = "Hosting: FTP & HTTP Modes",
                badge = "Hosting",
                icon = Icons.Default.Sync,
                items = listOf(
                    HelpTopicItem("Exclusive Hosting Modes", "Only one mode runs at a time (FTP Hosting, HTTP Hosting, or Casting). Starting a mode automatically stops any other active mode."),
                    HelpTopicItem("FTP Hosting (:2121)", "Share selected files/folders or full storage over FTP, configure anonymous or user/password access, and set an isolated uploads folder."),
                    HelpTopicItem("HTTP Hosting (:8080)", "Host an unzipped website directory (HTML/CSS/JS/assets) or single HTML file with live in-app preview and optional PIN gate."),
                    HelpTopicItem("SyncPlay Party", "Launching SyncPlay from File Manager opens immediately via FTP Hosting; you can also run SyncPlay from either FTP Hosting or Casting with mandatory 6-digit PIN and frame-accurate transport.")
                )
            ),
            HelpGuideSection(
                id = "hosting_casting_omnios",
                category = "Hosting",
                title = "Casting & Receiver Mode",
                badge = "OmniOS",
                icon = Icons.Default.Cast,
                items = listOf(
                    HelpTopicItem("Built-In Screen & Web Cast", "Cast OmniOS directly onto the current device screen or open the LAN Web Cast URL in any browser with zero approval barrier."),
                    HelpTopicItem("Wired & Wireless Cast", "Project directly to USB-C DisplayPort / HDMI monitors via secondary Presentation (or USB adb reverse) and launch Android Miracast Wireless Display with no extra approval."),
                    HelpTopicItem("Same App (LAN) & Receiver Mode", "Switch between Host and Receiver roles with auto LAN NSD discovery, manual IP, and two-way QR (Show QR / Scan QR). App-to-App Cast asks permission on the Receiver before filling the display edge-to-edge with touch support."),
                    HelpTopicItem("Admin Console & Shared Files", "Toggle My Suite mini-apps (Markdown, Code, Table, Photo, File Converter, File Manager) and Desktop apps live, share selected files/folders for receiver download, and adjust touch gate, tri-route audio, brightness, and desktop style."),
                    HelpTopicItem("Locked SyncPlay Window", "Active SyncPlay media in Casting opens as a fullscreen OmniOS window that receivers can minimize to multitask but cannot close.")
                )
            ),
            HelpGuideSection(
                id = "desktop_and_ui",
                category = "Desktop & UI",
                title = "Android Desktop/DeX & 3D Gallery",
                badge = "DeX + 3D",
                icon = Icons.Default.DesktopWindows,
                items = listOf(
                    HelpTopicItem("Persistent Sidebar on New Windows", "Sidebar stays visible on new windows and folder navigation; auto-hides by default only inside Mini-App Create/Edit mode (toggleable via Triple-Line Menu button)."),
                    HelpTopicItem("Interactive 3D Picture Wall", "Tap the 3D Wall icon in Gallery to switch between the 3D Perspective Matrix Wall and 360° 3D Cylinder Ring with touch pan, tilt, pinch-zoom, and frustum culling."),
                    HelpTopicItem("Zero-Lag Hardware Thumbnails", "220x220 downsampled hardware-bitmap Coil requests + O(1) concurrent album preview cache + O(N) grouping pass."),
                    HelpTopicItem("Clean Minimalist Headers", "Single-row tonal command bars with minimalist single-line headings and system Back gesture support.")
                )
            ),
            HelpGuideSection(
                id = "video_and_music_players",
                category = "Media",
                title = "Media & 3D Players",
                badge = "Media3",
                icon = Icons.Default.OndemandVideo,
                items = listOf(
                    HelpTopicItem("Single Video Header & 3D Modes", "Dedicated single video title bar with Normal 2D, Red-Cyan Anaglyph, Red-Green 3D (Dubois matrix) & Side-by-Side VR."),
                    HelpTopicItem("Tracks & Speed", "Switch multi-language audio tracks, subtitles/CC, and playback speed (0.25x–3.0x)."),
                    HelpTopicItem("Swipe Gestures", "Left vertical = Brightness, Right vertical = Volume, Horizontal = Scrub."),
                    HelpTopicItem("Music Sleep Timer & N-Track Stop", "Stop playback automatically after a custom number of minutes OR after N queue tracks in both Pop-Up Window and Fullscreen Studio modes.")
                )
            ),
            HelpGuideSection(
                id = "studios_and_html",
                category = "Studios",
                title = "Studios & HTML Preview",
                badge = "Studios",
                icon = Icons.Default.Html,
                items = listOf(
                    HelpTopicItem("1:1 Synchronized Line Numbers", "Markdown Studio and Code Studio use TextLayoutResult baseline coordinates so soft-wrapped lines never desynchronize line numbers."),
                    HelpTopicItem("Photo Studio + Canvas Layering", "Edit existing photos or frame/composite multiple images on White, Dark, Black, or Cream 1:1 / 16:9 canvases."),
                    HelpTopicItem("HTML5 Previewer", "Rendered WebView + Raw HTML source inspector with DOM Console Overlay."),
                    HelpTopicItem("Table Studio", "Interactive CSV/TSV spreadsheet grid with column/row insertion and Triple-Line Menu sidebar toggle.")
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
                    HelpTopicItem("PhotoEditor (MIT)", "Canvas brush, filter matrix, canvas framing & sticker layers by Burhanuddin Rashid."),
                    HelpTopicItem("CoilCompose & Media3 (Apache 2.0)", "Downsampled hardware-bitmap loader, 3D Gallery Wall textures & ExoPlayer DSP."),
                    HelpTopicItem("Ktor, OkHttp & Apache FtpServer (Apache 2.0)", "Embedded HTTP/WebSocket OmniOS & SyncPlay server, LAN invite client & RFC-959 FTP daemon."),
                    HelpTopicItem("CameraX, ML Kit & ZXing Core (Apache 2.0)", "On-device two-way QR scanning and ISO/IEC 18004 QR code generation."),
                    HelpTopicItem("Google Fonts, PDFBox & Apache POI", "Offline typography and PDF/Office engines (Apache 2.0 / OFL 1.1).")
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
