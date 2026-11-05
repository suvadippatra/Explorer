package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TocHeaderItem(
    val level: Int,
    val title: String,
    val lineIndex: Int,
    val charOffset: Int
)

object TocScanner {
    fun scanHeaders(rawMarkdown: String): List<TocHeaderItem> {
        val items = mutableListOf<TocHeaderItem>()
        if (rawMarkdown.isEmpty()) return items

        val lines = rawMarkdown.lines()
        val headerRegex = Regex("^(#{1,6})\\s+(.*)$")
        var runningOffset = 0

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val match = headerRegex.find(trimmed)
            if (match != null) {
                val hashes = match.groupValues[1]
                val title = match.groupValues[2].trim()
                items.add(TocHeaderItem(hashes.length, title, index, runningOffset))
            }
            runningOffset += line.length + 1 // +1 for newline
        }
        return items
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownTocDrawerContent(
    rawMarkdown: String,
    onHeaderClick: (TocHeaderItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val allHeaders = remember(rawMarkdown) {
        TocScanner.scanHeaders(rawMarkdown)
    }

    val filteredHeaders = remember(allHeaders, searchQuery) {
        if (searchQuery.isBlank()) allHeaders
        else allHeaders.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 280.dp, max = 340.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Table of Contents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Quick Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter headers...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            if (filteredHeaders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (allHeaders.isEmpty()) "No headers found (# Heading)" else "No matching headers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredHeaders) { item ->
                        val indentDp = ((item.level - 1) * 14).dp
                        val badgeColor = when (item.level) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onHeaderClick(item) }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .padding(start = indentDp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "H${item.level}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = if (item.level <= 2) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    else MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Line ${item.lineIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
