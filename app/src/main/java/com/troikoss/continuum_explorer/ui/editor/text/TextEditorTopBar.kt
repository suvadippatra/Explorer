package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar

/**
 * Unified 48dp Single-Row Tonal Command Bar for Markdown & Text Studio.
 * Eliminates stacked top bars, redundant title icons, and subtitles.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onToggleSearch: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    onToggleFormatDock: (() -> Unit)? = null,
    isFormatDockVisible: Boolean = true,
    onCloseEditor: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    SingleRowCommandBar(
        modifier = modifier.testTag("text_editor_command_bar"),
        navigationContent = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("text_editor_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle Sidebar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        titleContent = {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (isLargeScreen) 240.dp else 130.dp)
            )
        },
        centerContent = {
            if (isMarkdown && isLargeScreen) {
                val modes = listOf(
                    TextDisplayMode.EDIT to "Edit",
                    TextDisplayMode.SPLIT to "Split",
                    TextDisplayMode.PREVIEW to "Preview"
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("text_editor_mode_segmented")
                ) {
                    modes.forEachIndexed { idx, (mode, label) ->
                        SegmentedButton(
                            selected = displayMode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                            icon = {},
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        actionsContent = {
            if (isMarkdown && !isLargeScreen) {
                IconButton(
                    onClick = {
                        val next = when (displayMode) {
                            TextDisplayMode.EDIT -> TextDisplayMode.PREVIEW
                            TextDisplayMode.PREVIEW -> TextDisplayMode.EDIT
                            TextDisplayMode.SPLIT -> TextDisplayMode.EDIT
                        }
                        onModeChange(next)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (displayMode == TextDisplayMode.PREVIEW) Icons.Default.Edit else Icons.Default.Visibility,
                        contentDescription = if (displayMode == TextDisplayMode.PREVIEW) "Edit" else "Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (onToggleSearch != null) {
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("text_editor_find_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Find",
                        tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            IconButton(
                onClick = onUndo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("text_editor_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("text_editor_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onToggleFormatDock != null && isLargeScreen) {
                IconButton(
                    onClick = onToggleFormatDock,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("text_editor_format_dock_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFormat,
                        contentDescription = "Format Bar",
                        tint = if (isFormatDockVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (isMarkdown && onToggleToc != null) {
                IconButton(
                    onClick = onToggleToc,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("text_editor_toc_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = "Contents",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onSave,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("text_editor_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("text_editor_overflow_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    if (onToggleFormatDock != null && !isLargeScreen) {
                        DropdownMenuItem(
                            text = { Text(if (isFormatDockVisible) "Hide Format Bar" else "Show Format Bar") },
                            leadingIcon = { Icon(Icons.Default.TextFormat, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onToggleFormatDock()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isFullScreen) "Show Tab Bar" else "Fullscreen") },
                        leadingIcon = {
                            Icon(
                                if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            SettingsManager.toggleFullScreenTabBar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onViewPath?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move File") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onChangePath?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open External") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenExternal()
                        }
                    )
                    if (onCloseEditor != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Close", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMoreMenu = false
                                onCloseEditor()
                            }
                        )
                    }
                }
            }
        }
    )
}
