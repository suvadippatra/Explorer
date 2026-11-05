package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhotoEditorToolPalette(
    stateController: PhotoEditorStateController,
    onAddTextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            // Category Selector Tabs
            ScrollableTabRow(
                selectedTabIndex = stateController.activeTool.ordinal,
                edgePadding = 0.dp
            ) {
                PhotoTool.values().forEach { tool ->
                    Tab(
                        selected = stateController.activeTool == tool,
                        onClick = { stateController.activeTool = tool },
                        text = { Text("${tool.iconEmoji} ${tool.label}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tool Options Panel
            when (stateController.activeTool) {
                PhotoTool.BRUSH -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ShapeType.FREEHAND to "✏️ Free",
                                ShapeType.LINE to "📏 Line",
                                ShapeType.RECTANGLE to "⬜ Rect",
                                ShapeType.CIRCLE to "⭕ Circle",
                                ShapeType.ARROW to "↗ Arrow"
                            ).forEach { (shape, label) ->
                                FilterChip(
                                    selected = stateController.selectedShapeType == shape && !stateController.isEraser,
                                    onClick = {
                                        stateController.selectedShapeType = shape
                                        stateController.isEraser = false
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            FilterChip(
                                selected = stateController.isEraser,
                                onClick = { stateController.isEraser = true },
                                label = { Text("🧹 Eraser", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // Color Palette
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Color.Red, Color.Green, Color.Blue, Color.Yellow,
                                Color.Cyan, Color.Magenta, Color.White, Color.Black, Color(0xFFFF9800)
                            ).forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (stateController.brushColor == col) 3.dp else 1.dp,
                                            color = if (stateController.brushColor == col) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            stateController.brushColor = col
                                            stateController.isEraser = false
                                        }
                                )
                            }
                        }

                        // Size Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size: ${stateController.brushSize.toInt()}px", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = stateController.brushSize,
                                onValueChange = { stateController.brushSize = it },
                                valueRange = 2f..60f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                PhotoTool.FILTER -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PhotoFilterType.values().forEach { flt ->
                            FilterChip(
                                selected = stateController.activeFilter == flt,
                                onClick = { stateController.activeFilter = flt },
                                label = { Text(flt.displayName) }
                            )
                        }
                    }
                }
                PhotoTool.TEXT -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddTextClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Text Overlay")
                        }
                        if (stateController.textOverlays.isNotEmpty()) {
                            Text("${stateController.textOverlays.size} text item(s)", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { stateController.textOverlays.clear() }) {
                                Text("Clear")
                            }
                        }
                    }
                }
                PhotoTool.STICKER -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("🔥", "❤️", "⭐", "🎉", "👍", "😎", "📷", "💡", "🚀", "🌟").forEach { emoji ->
                            SuggestionChip(
                                onClick = {
                                    stateController.stickerOverlays.add(StickerOverlayItem(emojiOrSticker = emoji))
                                },
                                label = { Text(emoji, fontSize = 18.sp) }
                            )
                        }
                    }
                }
                PhotoTool.CROP_ROTATE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { stateController.rotationAngle = (stateController.rotationAngle + 90) % 360 },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate 90°")
                        }

                        FilterChip(
                            selected = stateController.isFlipHorizontal,
                            onClick = { stateController.isFlipHorizontal = !stateController.isFlipHorizontal },
                            label = { Text("↔ Flip Horiz") }
                        )

                        FilterChip(
                            selected = stateController.isFlipVertical,
                            onClick = { stateController.isFlipVertical = !stateController.isFlipVertical },
                            label = { Text("↕ Flip Vert") }
                        )
                    }
                }
                PhotoTool.WATERMARK -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = stateController.watermarkText,
                            onValueChange = { stateController.watermarkText = it },
                            label = { Text("Watermark Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                PhotoTool.RESIZE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = stateController.targetWidthInput,
                            onValueChange = { stateController.targetWidthInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Width (px)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stateController.targetHeightInput,
                            onValueChange = { stateController.targetHeightInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Height (px)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
