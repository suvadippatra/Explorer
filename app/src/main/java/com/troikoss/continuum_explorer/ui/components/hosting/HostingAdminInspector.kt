package com.troikoss.continuum_explorer.ui.components.hosting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.server.omnios.OmniOSAdminConfigStore
import com.troikoss.continuum_explorer.server.omnios.OmniOSAppCatalog
import com.troikoss.continuum_explorer.server.omnios.OmniOSAppDescriptor
import com.troikoss.continuum_explorer.server.omnios.OmniOSAudioRoute
import com.troikoss.continuum_explorer.server.omnios.OmniOSAudioRoutingManager
import java.io.File
import kotlin.math.roundToInt

/**
 * Ultra-Clean Tonal Flat Material 3 Host Admin Inspector for OmniOS Casting.
 *
 * Controls:
 * - My Suite Mini-Apps & Core OmniOS App Whitelist (pushed live over WebSocket)
 * - Shared Downloadable Files / Folders sandbox
 * - Touch Input Gate, Tri-Route Audio, Software Display Brightness, DE Theme, Dark Mode,
 *   and Client Settings Tab Visibility.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostingAdminInspector(
    onAddSharedFile: () -> Unit,
    onAddSharedFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val admin by OmniOSAdminConfigStore.settings.collectAsState()
    val audioState by OmniOSAudioRoutingManager.audioState.collectAsState()
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. MY SUITE MINI-APPS WHITELIST ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Suite Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = "${OmniOSAppCatalog.mySuiteApps.count { it.id in admin.allowedApps }}/${OmniOSAppCatalog.mySuiteApps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = cs.primary
                    )
                }

                OmniOSAppCatalog.mySuiteApps.forEach { app ->
                    AdminAppWhitelistRow(
                        app = app,
                        checked = app.id in admin.allowedApps,
                        onCheckedChange = { OmniOSAdminConfigStore.setAppAllowed(app.id, it) }
                    )
                }
            }
        }

        // --- 2. OMNIOS CORE APPS WHITELIST ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Desktop Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = "${OmniOSAppCatalog.coreApps.count { it.id in admin.allowedApps }}/${OmniOSAppCatalog.coreApps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = cs.primary
                    )
                }

                OmniOSAppCatalog.coreApps.forEach { app ->
                    AdminAppWhitelistRow(
                        app = app,
                        checked = app.id in admin.allowedApps,
                        onCheckedChange = { OmniOSAdminConfigStore.setAppAllowed(app.id, it) }
                    )
                }
            }
        }

        // --- 3. SHARED FILES ACCESS FOR RECEIVER DOWNLOADS ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shared Files",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    if (admin.sharedCastPaths.isNotEmpty()) {
                        TextButton(
                            onClick = { OmniOSAdminConfigStore.clearSharedPaths() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("admin_clear_shared_files")
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = cs.error)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAddSharedFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_add_shared_file_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("File", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onAddSharedFolder,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_add_shared_folder_button")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Folder", maxLines = 1)
                    }
                }

                if (admin.sharedCastPaths.isEmpty()) {
                    Text(
                        text = "No files shared",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        admin.sharedCastPaths.forEach { path ->
                            val f = File(path)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cs.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (f.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = f.name.ifBlank { path },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { OmniOSAdminConfigStore.removeSharedPath(path) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove shared path",
                                            tint = cs.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. GOVERNANCE & DISPLAY CALIBRATION ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Display & Input",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Touch Input",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface
                    )
                    Switch(
                        checked = admin.touchInputEnabled,
                        onCheckedChange = { OmniOSAdminConfigStore.setTouchInputEnabled(it) },
                        modifier = Modifier.testTag("admin_touch_input_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface
                    )
                    Switch(
                        checked = admin.darkMode,
                        onCheckedChange = { OmniOSAdminConfigStore.setDarkMode(it) },
                        modifier = Modifier.testTag("admin_dark_mode_switch")
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brightness", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${admin.brightness}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }
                    Slider(
                        value = admin.brightness.toFloat(),
                        onValueChange = { OmniOSAdminConfigStore.setBrightness(it.roundToInt()) },
                        valueRange = 15f..100f,
                        modifier = Modifier.testTag("admin_brightness_slider")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio Route", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = audioState.activeOutputLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OmniOSAudioRoute.entries.forEach { route ->
                            FilterChip(
                                selected = admin.audioRoute.equals(route.id, ignoreCase = true),
                                onClick = { OmniOSAudioRoutingManager.setRoute(route) },
                                label = { Text(route.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_audio_${route.id}")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Desktop Style", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "dex" to "DeX",
                            "macos" to "macOS",
                            "windows" to "Windows",
                            "linux" to "Linux"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = admin.theme == id,
                                onClick = { OmniOSAdminConfigStore.setTheme(id) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("admin_theme_$id")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Client Settings", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "appearance" to "Appearance",
                            "animation" to "Motion",
                            "sound" to "Sound",
                            "storage" to "Storage",
                            "host" to "Host Info"
                        ).forEach { (key, label) ->
                            val enabled = admin.settingsVisibility[key] != false
                            FilterChip(
                                selected = enabled,
                                onClick = { OmniOSAdminConfigStore.setSettingSectionVisible(key, !enabled) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("admin_vis_$key")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAppWhitelistRow(
    app: OmniOSAppDescriptor,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("admin_app_${app.id}")
        )
    }
}
