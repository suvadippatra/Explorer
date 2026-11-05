package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.StorageProviders
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.R
import kotlinx.coroutines.launch

private val TAB_SLOT_MIN = 150.dp  // minimum total slot width per tab so names are clearly readable
private val TAB_SLOT_MAX = 220.dp  // maximum total slot width per tab
private val ADD_BUTTON_WIDTH = 40.dp

@Composable
fun TabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    appState: FileExplorerState
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.BottomStart
    ) {
        // Shrink tabs proportionally as more are added, scroll when they hit minimum.
        val slotWidth: Dp = if (tabs.isEmpty()) TAB_SLOT_MAX else {
            val available = maxWidth - ADD_BUTTON_WIDTH
            (available / tabs.size).coerceIn(TAB_SLOT_MIN, TAB_SLOT_MAX)
        }

        LaunchedEffect(selectedTabIndex, tabs.size) {
            if (selectedTabIndex in tabs.indices && scrollState.viewportSize > 0) {
                val tabPx = with(density) { slotWidth.toPx() }
                val tabStartPx = (selectedTabIndex * tabPx).toInt()
                val tabEndPx = tabStartPx + tabPx.toInt()
                val viewportStart = scrollState.value
                val viewportEnd = viewportStart + scrollState.viewportSize
                if (tabStartPx < viewportStart) {
                    scrollState.animateScrollTo(tabStartPx.coerceAtLeast(0))
                } else if (tabEndPx > viewportEnd) {
                    val targetScroll = (tabEndPx - scrollState.viewportSize).coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(targetScroll)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.first().scrollDelta
                                    coroutineScope.launch {
                                        scrollState.scrollBy(delta.y * 60f)
                                    }
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEachIndexed { index, title ->
                    val tabIcon = when {
                        appState.currentArchiveFile != null -> Icons.Default.FolderZip
                        else -> Icons.Default.Folder
                    }

                    TabItem(
                        text = title,
                        icon = tabIcon,
                        slotWidth = slotWidth,
                        selected = (selectedTabIndex == index),
                        onClick = { onTabSelected(index) },
                        onClose = { onCloseTab(index) }
                    )
                }

                IconButton(
                    onClick = onAddTab,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.new_tab),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private val TabShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

@Composable
private fun TabItem(
    text: String,
    icon: ImageVector,
    slotWidth: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val isColorful = SettingsManager.isColorfulBarsEnabled.value

    val backgroundColor = when {
        selected && isColorful -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val textColor = when {
        selected && isColorful -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val activeBorderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    // slotWidth = visual tab width + 4dp side padding (2dp each side)
    Box(
        modifier = Modifier
            .width(slotWidth)
            .padding(start = 2.dp, end = 2.dp, top = 6.dp)
            .clip(TabShape)
            .height(34.dp)
            .background(backgroundColor)
            .drawBehind {
                if (selected) {
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = activeBorderColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = stroke
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = textColor,
                modifier = Modifier.weight(1f)
            )

            if (selected) {
                Spacer(Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClose() },
                    tint = textColor
                )
            }
        }
    }
}