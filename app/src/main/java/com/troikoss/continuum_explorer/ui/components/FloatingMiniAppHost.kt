package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.rules.FloatingPanelSession
import com.troikoss.continuum_explorer.utils.FileExplorerState
import kotlin.math.roundToInt

/**
 * Renders a movable, resizable floating desktop panel on Tablet & DeX when the user selects
 * WorkspaceWindowMode.FLOATING_PANELS_DEX_TABLET_TABS_MOBILE (Option 3) in Settings.
 */
@Composable
fun FloatingMiniAppHost(
    session: FloatingPanelSession,
    appState: FileExplorerState,
    onExpandToFullTab: (FloatingPanelSession) -> Unit,
    onClose: () -> Unit
) {
    var offsetX by remember(session.id) { mutableFloatStateOf(48f) }
    var offsetY by remember(session.id) { mutableFloatStateOf(40f) }
    var panelWidthDp by remember(session.id) { mutableStateOf(720.dp) }
    var panelHeightDp by remember(session.id) { mutableStateOf(520.dp) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width = panelWidthDp, height = panelHeightDp)
                .shadow(18.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Draggable Window Title Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .pointerInput(session.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                                offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "Drag Window",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onExpandToFullTab(session) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.OpenInFull,
                                    contentDescription = "Dock to Tab",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Window",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Window Body
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (session.file != null) {
                        ViewOnlyFileViewer(
                            file = session.file,
                            appState = appState,
                            startInEditMode = session.openInEditMode,
                            onClose = onClose
                        )
                    }
                }

                // Bottom-Right Resize Grip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .pointerInput(session.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val density = this.density
                                panelWidthDp = (panelWidthDp + (dragAmount.x / density).dp).coerceIn(420.dp, 1200.dp)
                                panelHeightDp = (panelHeightDp + (dragAmount.y / density).dp).coerceIn(320.dp, 900.dp)
                            }
                        }
                )
            }
        }
    }
}
