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
 * Comprehensive In-App Help & Documentation Page + Open-Source Knowledge List.
 * Updated with every code modification including:
 * - Minimalist 3-Tab File Converter (Student Exam Passport Photo 20–50 KB & Signature 10–20 KB Squoosh Resizer, PDF Compressor, VERT Single-File Dynamic Picker & Batch/Combine to PDF)
 * - 5 Separated Studios in My Suite (File Converter, Markor Text & Markdown Studio, Code Studio, CSV & Table Studio, PhotoEditor Image Studio) with Minimalist Landing Pages & List/Grid Recent Toggle
 * - Desktop-Class PDF Viewer (Single Unified Header, Page Jump, 2-Page Spread, Night/Sepia Modes, Search, Print & Quick Compress)
 * - Interactive CSV Table Preview & Editor Routing
 * - 3-Mode Music Player (Persistent Bottom Mini-Bar, Draggable Floating PiP Card, and Spotify-Style Fullscreen Player)
 * - Redesigned FTP/HTTP Server (Unified Upload Toggle, 0-File Hosting, Android 8–15 MJPEG/WebSocket Screen Sharing with FPS & Compression Sliders, Host Device Telemetry & Remote Browser Mini-Apps)
 * - Redesigned 'Add FTP Server' Client (Native FTP/HTTP File Browser + 1-Tap Built-in WebView Browser)
 * - Redesigned Universal File & Folder Picker (List/Grid Toggle with Live Thumbnails, Sort & Breadcrumbs)
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "squoosh_vert_file_converter",
                title = "1. Minimalist 3-Tab File Converter (Squoosh Exact-KB Resizer + VERT Engine)",
                badge = "File Converter",
                icon = Icons.Default.Transform,
                summary = "Redesigned 3-tab minimalist File Converter combining Squoosh-style binary-search exact-KB compression for student exam forms, PDF size compression, VERT dynamic single-file conversion, and batch multi-image-to-PDF combining.",
                bullets = listOf(
                    "Tab 1 — Quick Presets (Student Exam Photo, Signature & PDF Size): One-tap 'Exam Photo (20–50 KB, 200×230 px)', 'Signature (10–20 KB, 140×60 px with high-contrast white paper cleanup)', custom Target KB slider (8–300 KB) using Squoosh binary-search JPEG/WEBP quantization + progressive downscaling, and PDF Size Compressor (100/150/200 DPI re-rasterization).",
                    "Live Before vs. After Size & Dimensions Preview Card: Shows original vs. compressed thumbnail, exact pixel dimensions (W×H px), KB size reduction, and percentage saved badge.",
                    "Tab 2 — Single File (VERT Dynamic Format Picker): Select any file and the engine automatically detects its category (Image, Document/Data, Audio/Video, or Archive) and presents only compatible target format chips with live conversion.",
                    "Tab 3 — Batch Convert & Combine Images into 1 PDF: Queue multiple files with a global 'Convert All To' selector, per-file format overrides, or combine multiple selected photos/scans into a single multi-page PDF document.",
                    "Guaranteed Writable Output Folder: Automatically saves converted files to the source folder, './Converted/', or falls back to '/Documents/ContinuumSuite/Converted/' on scoped storage, with instant 'Open' and 'Share' buttons on every completed job."
                )
            ),
            HelpGuideSection(
                id = "my_suite_five_studios",
                title = "2. My Suite — 5 Separated Studios, Landing Pages & List/Grid Recent View",
                badge = "My Suite",
                icon = Icons.Default.AutoAwesomeMosaic,
                summary = "My Suite is organized into 5 distinct Studios, each with its own minimalist Landing Page, a 50.dp Triple-Line Menu heading bar, and a List/Grid switcher for Recently Edited & Created files.",
                bullets = listOf(
                    "5 Dedicated Studios: (1) File Converter, (2) Markor Text & Markdown Studio (.md, .txt, .todo.txt, .org, .adoc), (3) Code Studio (.kt, .java, .py, .js, .ts, .html, .css, .json, .xml, .yaml, .sh), (4) CSV & Table Studio (.csv, .tsv), and (5) PhotoEditor Image Studio (.png, .jpg, .webp, .bmp).",
                    "Minimalist Landing Page for Every Studio: Opening any Studio from My Suite displays a clean landing screen with 'Create New File' (including blank canvas generation for Image Studio), 'Open Existing File to Edit' (with live thumbnails), and that Studio's recent files.",
                    "Unified 50.dp Triple-Line Menu Heading Bar: All active editors (Markor, Code Studio, CSV Studio, PhotoEditor, File Converter) use the 50.dp top heading bar with the Triple-Line Menu Button on the left instead of a Close/X button.",
                    "Interactive CSV Preview & Exclusive CSV Studio Routing: Tapping a .csv/.tsv file (or viewing it in Homepage Quick Preview) renders an interactive spreadsheet table with sticky headers, clickable column sorting (▲/▼), alternating row striping, row/column count badge, and search filter; clicking Edit opens CSV & Table Studio."
                )
            ),
            HelpGuideSection(
                id = "desktop_pdf_and_spotify_audio",
                title = "3. Desktop-Class PDF Viewer & 3-Mode Spotify-Style Music Player",
                badge = "Media & PDF",
                icon = Icons.Default.LibraryMusic,
                summary = "Desktop-grade PDF reading controls with zero duplicate headers, plus a 3-mode audio player that transitions between a Bottom Mini-Bar, Draggable Floating PiP Card, and Spotify-style Fullscreen Player.",
                bullets = listOf(
                    "Desktop-Class PDF Viewer: Eliminates duplicate top bars and provides a single unified header plus a collapsible desktop control bar with Page Jump ('Page X / Y'), Continuous Vertical / Single Page / 2-Page Desktop Spread modes, Zoom (- / 100% / +), 90° Rotation, Day / Night Inverted / Sepia reading modes, Thumbnail strip, PDF text search, Print (PrintManager), and Quick Compress PDF.",
                    "Mode 1 — Persistent Bottom Mini-Player Bar: Tapping any audio file (.mp3, .wav, .flac, .m4a, .aac, .ogg) starts playback in a sleek bottom bar inside Explorer so you can keep browsing files.",
                    "Mode 2 — Draggable Floating PiP Mini-Card: Minimize the bottom bar into a draggable floating Picture-in-Picture card over your files, with 1-tap buttons to dock back to the Bottom Bar or expand to Fullscreen.",
                    "Mode 3 — Spotify-Style Fullscreen Music Player: Features dynamic dominant-color gradient background extracted from embedded ID3 album art, audio format & bitrate badge (e.g. 'FLAC • 1411 kbps' / 'MP3 • 320 kbps'), interactive seek slider with elapsed and remaining (-MM:SS) timestamps, shuffle, repeat (Off/All/One), playback speed (0.75x–2.0x), and an auto-populated Folder Audio Queue."
                )
            ),
            HelpGuideSection(
                id = "ftp_http_server_and_client",
                title = "4. FTP/HTTP Server, Live Screen Sharing, Remote Mini-Apps & FTP Client",
                badge = "Server & FTP",
                icon = Icons.Default.Dns,
                summary = "Unified upload controls, 0-file server hosting, Android 8–15 MJPEG/WebSocket screen sharing with FPS & compression sliders, live host telemetry, 5 remote browser Mini-Apps, and a native FTP/HTTP client.",
                bullets = listOf(
                    "Unified Upload Toggle & Live Upload Progress Bar: Consolidates browser multipart uploads and FTP write permissions into a single 'Allow Browser & FTP Uploads' switch with a configurable Upload Destination Folder and live XMLHttpRequest upload progress bar.",
                    "Zero-File Server Hosting & 5 Remote Browser Mini-Apps: Start the server even with 0 selected files/folders. Enable 'Allow Mini-Apps in Browser' so visitors can use (1) Markor Markdown Studio, (2) Code Studio, (3) CSV Table Editor, (4) Image KB Resizer & Filter Tool, and (5) Host File Converter ('/api/miniapp/convert') from any web browser.",
                    "Live Screen Sharing with FPS, Compression & Snapshot Controls: Optional admin-enabled live screen sharing across Android 8–15 (with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION on Android 10+) featuring adjustable Frame Rate (5–30 FPS), JPEG Compression Quality (30%–95%), and Resolution (720p / 1080p / Native), plus Pause/Resume, Snapshot download, 90° Rotation, and Fullscreen controls on the web portal.",
                    "Host Telemetry & Inline Web File Previews: Displays live Host Device Name, Battery % + Charging status, and Free/Total Storage with a visual progress bar, plus inline modal previews for images, video, audio, PDF, CSV tables, Markdown, and source code.",
                    "Redesigned 'Add FTP Server' Client: Connects to Explorer hosts and third-party FTP/HTTP servers using a real Native File Browser first (supporting folder navigation, file download, file upload, and inline remote file preview), with a 1-tap 'Open in Built-in Web Browser' (WebView) toggle."
                )
            ),
            HelpGuideSection(
                id = "universal_picker_and_dex_rules",
                title = "5. Universal File Picker, DeX Standalone Windows & GitHub Hygiene",
                badge = "UX & Build",
                icon = Icons.Default.Layers,
                summary = "List/Grid thumbnails in all in-app file pickers, standalone DeX window chrome without duplicate tab bars, and clean GitHub repository exclusions.",
                bullets = listOf(
                    "Universal File & Folder Picker Redesign: Both InAppFilePickerDialog and FullPageFilePicker feature List/Grid view toggles with live image/video/APK thumbnails, Sort options (Name, Date, Size, Type), clickable Breadcrumbs, and Quick Storage chips (Internal, Downloads, Documents, Pictures, DCIM, Music).",
                    "Standalone DeX Window Chrome Rule: When a Mini-App or file editor opens in a separate OS window on DeX/Desktop, the top Explorer TabBar is hidden so it looks and behaves like a standalone native desktop app.",
                    "GitHub Repository Hygiene (.gitignore & README): Automatically excludes '.aistudio/', 'public/assets/aistudio/', '*.apk', '*.aab', and reference master folders from Git pushes while keeping them available in the AI Studio workspace, alongside a clean human-written README and single-line fork attribution."
                )
            ),
            HelpGuideSection(
                id = "open_source_knowledge_list",
                title = "6. Open-Source Knowledge & Libraries Attribution List",
                badge = "Open Source",
                icon = Icons.Default.Code,
                summary = "Complete list of open-source projects, architectural references, and libraries powering Explorer, My Suite, File Converter, PDF Viewer, Audio Player, and FTP/HTTP Server.",
                bullets = listOf(
                    "Continuum Explorer (https://github.com/troikoss/Continuum-Explorer — Upstream Base Project): This application is a forked and expanded version of Continuum Explorer.",
                    "Squoosh (https://github.com/GoogleChromeLabs/squoosh — Apache-2.0 inspiration): Architectural model for the binary-search exact-KB image quantization, progressive resolution scaling, and passport photo (20–50 KB) / signature (10–20 KB) compression engine in SquooshImageCompressor.kt.",
                    "VERT (https://github.com/VERT-sh/VERT — AGPL-3.0 inspiration): Architectural model for the 100% local, zero-cloud dynamic single-file and batch format converter in FileConverterScreen.kt and FileConverterDispatcher.kt.",
                    "Markor (https://github.com/gsantner/markor — Apache-2.0 inspiration) & Markwon (io.noties.markwon:core:4.6.2 — Apache-2.0): Powering the separated Markor Text & Markdown Studio, Code Studio, and CSV & Table Studio.",
                    "PDFBox-Android (com.tom-roush:pdfbox-android:2.0.27.0 — Apache-2.0) & Android Native PdfRenderer / PdfDocument / PrintManager: Powering the Desktop-Class PDF Viewer (2-page spread, night/sepia modes, text search, printing) and PDF Size Compressor.",
                    "AndroidX Media3 ExoPlayer (androidx.media3:media3-exoplayer:1.2.1 — Apache-2.0) & MediaMetadataRetriever: Powering the 3-Mode Music Player (Persistent Bottom Mini-Bar, Draggable Floating PiP Card, and Spotify-style Fullscreen Player with dominant-color artwork gradients) plus hardware MediaCodec audio/video conversion.",
                    "Apache POI (org.apache.poi:poi:5.2.3 & poi-ooxml:5.2.3 — Apache-2.0): Powers local DOCX Word and XLSX Excel reading and conversion to PDF, HTML, Markdown, CSV, JSON, and TXT.",
                    "Zip4j (net.lingala.zip4j:zip4j:2.11.6 — Apache-2.0), SevenZipJBinding-4Android & POSIX UStar TAR Engine: Powers archive browsing, AES-256 encrypted ZIP creation, and ZIP <-> TAR / GZ / TAR.GZ conversion.",
                    "Coil (io.coil-kt:coil-compose, coil-gif, coil-svg, coil-video:2.6.0 — Apache-2.0) & AndroidX ExifInterface (1.3.7): Powers live file picker thumbnails, SVG vector rasterization, and EXIF metadata inspection/stripping.",
                    "Ktor Server Netty & WebSockets (io.ktor:2.3.9 — Apache-2.0), Apache FTPServer (1.2.0) & Apache Commons Net FTPClient (3.10.0): Powers the embedded HTTP/FTP Server, MJPEG/WebSocket Live Screen Stream, Remote Browser Mini-Apps, and the Native FTP/HTTP Client."
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
                                text = "Explorer Help, Studios & Open-Source Knowledge Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Updated with Squoosh/VERT Converter, 5 My Suite Studios, Desktop PDF Viewer, 3-Mode Spotify Player, FTP/Screen Portal & Native FTP Client",
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
                    placeholder = { Text("Search help topics (e.g. Photo KB, Signature, PDF, Spotify, PiP, FTP, Screen Share, Squoosh, VERT)...") },
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
