package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TextEditorToolbar(
    onFormatClick: (prefix: String, suffix: String, placeholder: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { onFormatClick("# ", "", "Heading 1") },
                label = { Text("H1", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("## ", "", "Heading 2") },
                label = { Text("H2", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("### ", "", "Heading 3") },
                label = { Text("H3", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("**", "**", "Bold text") },
                label = { Text("B", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("*", "*", "Italic text") },
                label = { Text("I", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("~~", "~~", "Strikethrough") },
                label = { Text("S", fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { onFormatClick("> ", "", "Blockquote") },
                label = { Text("❝ Quote") }
            )
            AssistChip(
                onClick = { onFormatClick("`", "`", "code") },
                label = { Text("‹› Code") }
            )
            AssistChip(
                onClick = { onFormatClick("- ", "", "Item") },
                label = { Text("• Bullet List") }
            )
            AssistChip(
                onClick = { onFormatClick("1. ", "", "Item") },
                label = { Text("1. Numbered") }
            )
            AssistChip(
                onClick = { onFormatClick("- [ ] ", "", "Task") },
                label = { Text("☑ Task") }
            )
            AssistChip(
                onClick = { onFormatClick("[", "](https://example.com)", "Link Title") },
                label = { Text("🔗 Link") }
            )
            AssistChip(
                onClick = { onFormatClick("![", "](https://example.com/image.png)", "Image Alt") },
                label = { Text("🖼 Image") }
            )
            AssistChip(
                onClick = { onFormatClick("\n| Col 1 | Col 2 |\n| --- | --- |\n| ", " | Cell |\n", "Cell") },
                label = { Text("⊞ Table") }
            )
            AssistChip(
                onClick = { onFormatClick("$$ ", " $$", "E = mc^2") },
                label = { Text("∑ Math") }
            )
            AssistChip(
                onClick = { onFormatClick("\n---\n", "", "") },
                label = { Text("― Rule") }
            )
        }
    }
}
