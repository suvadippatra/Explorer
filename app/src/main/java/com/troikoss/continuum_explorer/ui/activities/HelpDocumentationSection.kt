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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp

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
    val summary: String,
    val items: List<HelpTopicItem>
)

/**
 * Redesigned Material 3 Help & Open-Source Reference Center.
 * Concise, scannable, collapsible cards with category filter chips and a rounded M3 search input.
 * Updated with all code modifications:
 * - Unified ServerManager Architecture (Independent & Coordinated Ktor HTTP/WS and Apache FTP lifecycles, Mutex concurrency, graceful drain, StateFlow UI)
 * - Beatsync-Inspired SyncPlay Watch & Listen Party (HTTP 206 Byte-Range pre-buffering, NTP clock sync, millisecond-scheduled execution, passcode auth)
 * - Upgraded DeX-Style Remote Browser Mini-Apps Studio (5-App Dock, Host File Picker, Live Markdown split preview, Interactive CSV Grid, Signature Ink Filter)
 * - Unified Mini-App & Editor Navigation Bars (Single Back button, ChevronLeft sidebar collapse, zero duplicate icons)
 * - Redesigned 3-Mode Studio Music Player & 3D Stereoscopic Video Player
 * - Live HTML5 File Previewer, Improved File Editors & 3-Tab File Converter
 * - Open-Source Knowledge & Attribution List
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Server & SyncPlay", "Media & 3D", "Studios & HTML", "Converter", "Open Source")
    }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "unified_server_and_syncplay",
                category = "Server & SyncPlay",
                title = "Unified ServerManager, SyncPlay Party & Web Studio",
                badge = "Server & SyncPlay",
                icon = Icons.Default.Sync,
                summary = "Central HTTP/FTP lifecycle manager, Beatsync-style group media playback, and 5-app DeX web workspace.",
                items = listOf(
                    HelpTopicItem(
                        "Unified ServerManager Architecture",
                        "Decouples Ktor Netty HTTP (:8080) and Apache FTP (:2121) implementations while unifying their independent and coordinated startup, graceful transfer-draining shutdown, restart, and reactive StateFlow monitoring."
                    ),
                    HelpTopicItem(
                        "SyncPlay Watch & Listen Party",
                        "Share any video or audio file from the File Context Menu ('SyncPlay Party') or Server Hub. Streams pre-buffered media via HTTP 206 Byte-Range requests and synchronizes Play, Pause, Seek, and Speed (0.5x–2.0x) across everyone."
                    ),
                    HelpTopicItem(
                        "NTP Clock Sync & Scheduled Execution",
                        "Uses WebSocket PING/PONG round-trip latency calibration and future server execution timestamps (executeAtServerEpochMs) so host and remote peers pre-seek and trigger playback at the exact same millisecond."
                    ),
                    HelpTopicItem(
                        "Passcode Auth & Guest Controls",
                        "Protect SyncPlay rooms with an optional passcode and toggle whether connected friends can control Play, Pause, Seek, and Speed for the entire group."
                    ),
                    HelpTopicItem(
                        "DeX-Style Web Mini-Apps Studio",
                        "5-app browser workspace dock featuring a Host File Picker, Markor Split-Preview Markdown Studio, Code Studio with JSON formatter, Interactive Editable CSV Spreadsheet Grid, Image Exact-KB & Signature Ink Filter Canvas, and Host Format Converter."
                    )
                )
            ),
            HelpGuideSection(
                id = "video_and_music_players",
                category = "Media & 3D",
                title = "3D Video Player & 3-Mode Music Studio",
                badge = "Media & 3D",
                icon = Icons.Default.MovieFilter,
                summary = "Stereoscopic Red-Green 3D video, swipe gestures, multi-track switching, and a 3-mode audio player.",
                items = listOf(
                    HelpTopicItem(
                        "Red-Green & Red-Cyan 3D Video",
                        "Convert any video in real time to Red-Green 3D, Red-Cyan 3D, or Side-by-Side (SBS) 3D with adjustable depth offset (3px / 6px / 10px)."
                    ),
                    HelpTopicItem(
                        "Video Swipe Gestures",
                        "Swipe vertically on the left for Brightness, vertically on the right for Volume, horizontally to scrub/seek, or double-tap for ±10s."
                    ),
                    HelpTopicItem(
                        "Audio, Captions & Speed",
                        "Switch embedded audio streams, toggle subtitle/caption tracks with live rendering, and adjust playback speed from 0.25x to 3.0x."
                    ),
                    HelpTopicItem(
                        "Music Pop-Up & Bottom Bar",
                        "Play audio in the Bottom Dock Bar or pop it out into a draggable Pop-Up Window with seek slider, ±10s skip, and speed controls."
                    ),
                    HelpTopicItem(
                        "Fullscreen Music Studio",
                        "Features rotating Vinyl disc mode, 5 Equalizer presets (Flat, Bass Boost, Vocal, Acoustic, Spatial), Sleep Timer, and a searchable folder queue."
                    )
                )
            ),
            HelpGuideSection(
                id = "studios_html_and_editors",
                category = "Studios & HTML",
                title = "My Suite Studios, HTML5 Preview & Editors",
                badge = "Studios",
                icon = Icons.Default.Web,
                summary = "Unified single-back navigation headers, live HTML5 rendering, and safe file editing across 5 studios.",
                items = listOf(
                    HelpTopicItem(
                        "Unified Navigation Bar",
                        "All Mini-Apps and Editors use a clean 50.dp header with a single Back arrow on the left and a dedicated Chevron sidebar collapse button."
                    ),
                    HelpTopicItem(
                        "Live HTML5 File Preview",
                        "Open or edit .html, .htm, and .xhtml files with Rendered WebView, Split View, and Source modes, plus Desktop 1280px viewport and Dark Canvas toggles."
                    ),
                    HelpTopicItem(
                        "Code Studio & Unsaved Guard",
                        "Live syntax highlighting, Material 3 Find & Replace bar with match counter, font zoom, dirty-state badge (•), and unsaved-changes confirmation."
                    ),
                    HelpTopicItem(
                        "CSV & Table Studio",
                        "Quote-aware CSV/TSV parser supporting embedded commas, row search filter, column sorting, row/column insertion, and M3 cell editing."
                    ),
                    HelpTopicItem(
                        "Markor, PhotoEditor & PDF",
                        "Markdown/Todo.txt formatting toolbar, non-destructive image filters/annotations, and Desktop PDF Viewer with 2-page spread & night mode."
                    )
                )
            ),
            HelpGuideSection(
                id = "squoosh_vert_converter",
                category = "Converter",
                title = "3-Tab File Converter (Squoosh & VERT)",
                badge = "Converter",
                icon = Icons.Default.Transform,
                summary = "Exact-KB exam photo/signature compression, dynamic single-file conversion, and batch PDF combining.",
                items = listOf(
                    HelpTopicItem(
                        "Exam Photo & Signature KB",
                        "1-tap presets for Exam Photo (20–50 KB) and Signature (10–20 KB with ink sharpening) plus custom KB slider and M3 pixel dimension inputs."
                    ),
                    HelpTopicItem(
                        "Single & Batch Conversion",
                        "Auto-detects compatible output formats for Images, Documents (PDF, DOCX, XLSX, MD, HTML, JSON, CSV, XML, YAML), Audio/Video, and Archives."
                    ),
                    HelpTopicItem(
                        "Enhanced MD ↔ HTML Engine",
                        "Full bidirectional support for Markdown pipe tables, h1–h6 headings, code blocks, strikethrough, blockquotes, and links."
                    ),
                    HelpTopicItem(
                        "Combine Images to PDF",
                        "Merge multiple selected photos or scanned pages into a single paginated PDF document with live Before/After size comparison."
                    )
                )
            ),
            HelpGuideSection(
                id = "open_source_knowledge_list",
                category = "Open Source",
                title = "Open-Source Knowledge & Libraries",
                badge = "Licenses",
                icon = Icons.Default.Code,
                summary = "Complete list of open-source projects, engines, protocols, and libraries powering Explorer.",
                items = listOf(
                    HelpTopicItem(
                        "Continuum Explorer (GPLv3)",
                        "github.com/troikoss/Continuum-Explorer — Base multi-tabbed & dual-pane file explorer architecture."
                    ),
                    HelpTopicItem(
                        "Markor (Apache-2.0)",
                        "github.com/gsantner/markor — Markdown, Plain Text, Todo.txt, and syntax highlighting editor patterns."
                    ),
                    HelpTopicItem(
                        "PhotoEditor (MIT)",
                        "github.com/burhanrashid52/PhotoEditor — Interactive brush, filter, crop, and text overlay canvas engine."
                    ),
                    HelpTopicItem(
                        "Ktor Netty & WebSockets + Apache FtpServer (Apache-2.0)",
                        "Managed by ServerManager for independent/coordinated HTTP 206 Byte-Range streaming, WebSocket SyncPlay, MJPEG screencasting, and passive-port FTP."
                    ),
                    HelpTopicItem(
                        "Beatsync-Inspired NTP Media Sync Architecture",
                        "Implements WebSocket PING/PONG clock offset estimation and future timestamp scheduling (executeAtServerEpochMs) for zero-drift group watch/listen parties."
                    ),
                    HelpTopicItem(
                        "Squoosh & VERT (Apache-2.0 / AGPL-3.0)",
                        "Architectural models for exact-KB binary-search image quantization and local multi-format file conversion."
                    ),
                    HelpTopicItem(
                        "AndroidX Media3 ExoPlayer (Apache-2.0)",
                        "Powers the 3-Mode Studio Music Player (Equalizer, Sleep Timer, Pop-Up), SyncPlay Host Audio Sync, and Stereoscopic 3D Video Player."
                    ),
                    HelpTopicItem(
                        "PDFBox-Android, Apache POI, Zip4j & Coil",
                        "Powers 2-page PDF viewing, DOCX/XLSX conversion, AES-256 ZIP/7Z/TAR archives, and hardware-accelerated media thumbnails."
                    )
                )
            )
        )
    }

    val filteredSections = remember(sections, searchFilter, selectedCategory) {
        sections.filter { sec ->
            val matchesCategory = selectedCategory == "All" || sec.category == selectedCategory
            val matchesSearch = searchFilter.isBlank() ||
                sec.title.contains(searchFilter, ignoreCase = true) ||
                sec.summary.contains(searchFilter, ignoreCase = true) ||
                sec.items.any {
                    it.label.contains(searchFilter, ignoreCase = true) ||
                        it.detail.contains(searchFilter, ignoreCase = true)
                }
            matchesCategory && matchesSearch
        }
    }

    var expandedIds by remember {
        mutableStateOf(sections.map { it.id }.toSet())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clean Material 3 Header & Search Bar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Help & Reference Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "ServerManager, SyncPlay Party, Studios, 3D video & open-source credits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            expandedIds = if (expandedIds.size == sections.size) {
                                emptySet()
                            } else {
                                sections.map { it.id }.toSet()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (expandedIds.size == sections.size) "Collapse All" else "Expand All",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search features (e.g. SyncPlay, ServerManager, 3D Video, HTML, KB)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchFilter.isNotEmpty()) {
                            IconButton(onClick = { searchFilter = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("help_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
        }

        // Collapsible Topic Cards
        filteredSections.forEach { section ->
            val isExpanded = searchFilter.isNotBlank() || section.id in expandedIds
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        expandedIds = if (section.id in expandedIds) {
                            expandedIds - section.id
                        } else {
                            expandedIds + section.id
                        }
                    }
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        section.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = section.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            section.items.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 17.sp,
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
    }
}
