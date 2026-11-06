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
 * Updated with every code modification including Direct Editor Launch, Origin Tab Memory,
 * APK Info & Package Installer Sheet, My Suite Recent Edited/Created Database,
 * Configurable Workspace Windowing Rules (Options 1/2/3), Unified Single Sidebar,
 * Android 8–15 Screen Sharing Fix, Hideable Video Player, and Interactive Responsive Web Portal.
 */
@Composable
fun HelpDocumentationSection(
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            HelpGuideSection(
                id = "file_open_edit_apk_rules",
                title = "1. File Opening, Direct Editing & APK Package Installer Rules",
                badge = "File Policy",
                icon = Icons.Default.Rule,
                summary = "Zero intermediate landing pages, smart 4-category file routing, direct three-dot Edit action, and dedicated APK Installer & Binary Info sheets.",
                bullets = listOf(
                    "Editable Files (Text, Markdown, Code, JSON, XML, CSV, Todo.txt, Images): Tapping the file in Explorer opens the Preview tab with an Edit button in the top bar. Selecting 'Edit' from the file's three-dot menu (or opening/creating from My Suite) launches directly into the Markor or PhotoEditor UI without any extra intermediate page.",
                    "Origin Tab Return Memory (Zero Memory Leaks): Every preview or direct-edit tab remembers its exact origin tab index. Pressing Back or closing the editor/viewer cleanly disposes the temporary tab and returns directly to the originating folder or My Suite tab.",
                    "APK Package Info & Installer Sheet: Tapping an .apk file never opens it as a ZIP folder structure. Instead, it displays the APK Info Sheet (app icon, package name, version name/code, min/target SDK, SHA-256 checksum) with a direct 'Install with Package Installer' button.",
                    "Playable/Viewable Media vs. Non-Editable Binary Files: Video, Audio, PDF, and ZIP/TAR/7Z/RAR archives open in their dedicated media/document/archive viewers (never raw binary text). Unsupported binary files open the File Info & 'Choose Preferred App to View' sheet."
                )
            ),
            HelpGuideSection(
                id = "workspace_windowing_and_sidebar_rules",
                title = "2. Workspace Windowing Modes, Unified Single Sidebar & Mini-App Rules",
                badge = "Architecture",
                icon = Icons.Default.Layers,
                summary = "User-selectable windowing modes across DeX, Tablet, and Mobile, a single non-duplicating sidebar, and formal Mini-App architecture rules.",
                bullets = listOf(
                    "Configurable Workspace Windowing Modes (Settings -> Editors & Handlers): Choose between Option 1 (Default: Separate multi-window on DeX, animated in-app tabs on Tablet & Mobile), Option 2 (Animated in-app tabs across DeX, Tablet & Mobile), or Option 3 (Draggable, resizable floating panels on DeX & Tablet, tabs on Mobile).",
                    "Unified Single Sidebar Rule: Tablet and DeX use a single collapsible left navigation pane (collapsed via the top-right ArrowBack button inside the sidebar and expanded via the top-left Menu button only when collapsed). Mobile uses a modal navigation drawer. Two sidebars never overlap.",
                    "Formal Rules Files Location: All workspace, windowing, sidebar, and Mini-App rules are modularized in app/src/main/java/com/troikoss/continuum_explorer/rules/ (MiniAppRulesSpec.md, WorkspaceRulesConfig.kt, MiniAppRegistry.kt, and WorkspaceWindowController.kt).",
                    "Archived Base64 Codec: The Base64 Codec tool is hidden from the active UI and preserved for future reference in app/src/main/java/com/troikoss/continuum_explorer/archive/base64/ArchivedBase64Codec.kt."
                )
            ),
            HelpGuideSection(
                id = "my_suite_and_recent_db",
                title = "3. My Suite Creative Hub, Recent Edited/Created Database & Quick Preview",
                badge = "My Suite",
                icon = Icons.Default.AutoAwesomeMosaic,
                summary = "Dedicated persistence database for created/edited files in My Suite and one-tap Homepage Quick Preview.",
                bullets = listOf(
                    "Dedicated Recent Edited & Created Files Database (SuiteRecentDatabase): The Recent section inside My Suite exclusively displays files created or edited within My Suite, Markor Text Editor, or PhotoEditor (with Created/Edited badges and timestamps), rather than device-wide MediaStore downloads.",
                    "Direct File Creation by Extension: Type any filename and extension in My Suite (e.g. notes.md, tasks.todo.txt, sheet.csv, script.kt) to create the file and launch directly into its matching editor.",
                    "Homepage Quick Preview Toggle: Tap the Eye (Quick Preview) icon in the Explorer TopBar to toggle the split-screen Quick Preview pane on the homepage.",
                    "Separate-Tab File Preview Action Bar: Shows File Name + Format/Size subtitle on the left, followed by Edit, Metadata View/Edit (inline rename & extension change, EXIF editor, GPS stripper, MD5/SHA-256 checksums), Delete, Share, Open External, and Fullscreen toggle."
                )
            ),
            HelpGuideSection(
                id = "ftp_website_server",
                title = "4. FTP/HTTP File Server, Self-Hosted Website & Live Screen Stream",
                badge = "Server Hub",
                icon = Icons.Default.Dns,
                summary = "Minimalist Segmented Mode Toggle, Android 8–15 MediaProjection fix, hideable live screen player, and interactive responsive Web Portal.",
                bullets = listOf(
                    "Explorer Notification Branding: Active foreground server notifications display 'Explorer' as the title with live IP and uptime.",
                    "Android 8–15 Live Screen Sharing Fix: Registers MediaProjection.Callback on a dedicated background HandlerThread prior to virtual display creation (required on Android 14/15) and provides both WebSocket (/screencast) and HTTP frame snapshot (/screencast-frame) streaming.",
                    "Hideable Live Screen Player on Hosted Page: The screen-share video player is hidden by default on the hosted file portal so file browsing stays clean, and can be toggled open or closed anytime via the 'Show/Hide Live Screen' toolbar button.",
                    "Interactive Responsive File Hosting Portal: Features clickable breadcrumbs, summary storage metrics, switchable Grid Cards and Table views, an inline Lightbox Media Preview modal for images/video/audio, and password-protected multi-file uploads."
                )
            ),
            HelpGuideSection(
                id = "window_fullscreen_layout",
                title = "5. Edge-to-Edge Window, Fullscreen Toggle & Layout Switcher",
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
                id = "apk_ci_cd",
                title = "6. Release APK Compression & Multi-ABI GitHub Actions CI/CD",
                badge = "Build & CI",
                icon = Icons.Default.BuildCircle,
                summary = "Compressed JNI packaging, per-ABI native splitting, and error-free GitHub Actions compilation for all Android architectures.",
                bullets = listOf(
                    "Release APK Size Compression & R8 Fix: Compresses APK size via per-ABI native .so filtering (-PabiTarget), Deflate-compressed JNI packaging (useLegacyPackaging = true), PNG crunching, and metadata exclusions.",
                    "Multi-Architecture GitHub Workflow (.github/workflows/android-build.yml): Builds arm64-v8a, armeabi-v7a, x86_64, x86, Universal APK, and Play Store AAB packages using JDK 21 and Gradle.",
                    "Automated 0.0.x+1 Versioning & Optional Releases: Base version starts at 0.0.1 and automatically increments to 0.0.x+1 on each workflow run with optional GitHub Release publishing."
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
                                text = "Explorer Help & Architecture Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Updated with Direct Editor Launch, APK Installer Sheet, Workspace Windowing Rules, Unified Sidebar & Responsive Web Portal",
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
                    placeholder = { Text("Search help topics (e.g. APK, Edit, Rules, DeX, Sidebar, Screen Share, My Suite)...") },
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
