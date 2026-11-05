package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TextEditorFooter(
    stats: TextStats,
    fontSizeSp: Float,
    onFontSizeDecrease: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${stats.wordCount} words | ${stats.charCount} chars | ${stats.lineCount} lines | ~${stats.readingTimeMin} min read",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onFontSizeDecrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.TextDecrease,
                        contentDescription = "Zoom Out Text",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "${fontSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onFontSizeIncrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = "Zoom In Text",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
