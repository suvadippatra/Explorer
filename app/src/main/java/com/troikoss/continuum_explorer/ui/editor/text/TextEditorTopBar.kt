package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TextEditorTopBar(
    fileName: String,
    isMarkdown: Boolean,
    displayMode: TextDisplayMode,
    isLargeScreen: Boolean,
    onMenuClick: () -> Unit,
    onModeChange: (TextDisplayMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onViewPath: (() -> Unit)? = null,
    onChangePath: (() -> Unit)? = null,
    onToggleToc: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth().height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Left Triple-Line Menu button per unified Mini-App Editor Heading rule
                IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Toggle Navigation Sidebar / File Tree",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = if (isMarkdown) Icons.Default.Article else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Dedicated Markdown Preview button (only active for Markdown documents)
                if (isMarkdown) {
                    IconButton(
                        onClick = {
                            val nextMode = when (displayMode) {
                                TextDisplayMode.EDIT -> TextDisplayMode.PREVIEW
                                TextDisplayMode.PREVIEW -> TextDisplayMode.EDIT
                                TextDisplayMode.SPLIT -> TextDisplayMode.EDIT
                            }
                            onModeChange(nextMode)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (displayMode == TextDisplayMode.PREVIEW) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (displayMode == TextDisplayMode.PREVIEW) "Edit Mode" else "Preview Markdown",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onModeChange(if (displayMode == TextDisplayMode.SPLIT) TextDisplayMode.EDIT else TextDisplayMode.SPLIT)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.VerticalSplit,
                            contentDescription = "Edit + Preview Split View",
                            tint = if (displayMode == TextDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (onToggleToc != null) {
                        IconButton(onClick = onToggleToc, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.FormatListNumbered,
                                contentDescription = "Table of Contents",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onRedo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Checkmark to save
                IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Full-Screen Toggle (Hides/Shows Top TabBar)
                val isFullScreen by com.troikoss.continuum_explorer.managers.SettingsManager.isFullScreenTabBarHidden
                IconButton(
                    onClick = { com.troikoss.continuum_explorer.managers.SettingsManager.toggleFullScreenTabBar() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                        tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isFullScreen) "Show Tab Bar" else "Full Screen (Hide Tab Bar)") },
                            leadingIcon = {
                                Icon(
                                    if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                com.troikoss.continuum_explorer.managers.SettingsManager.toggleFullScreenTabBar()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Path") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onViewPath?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Path / Move") },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onChangePath?.invoke()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Share Text") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onShare()
                            }
                        )
                        if (displayMode == TextDisplayMode.PREVIEW) {
                            DropdownMenuItem(
                                text = { Text("Open in External App") },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenExternal()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
