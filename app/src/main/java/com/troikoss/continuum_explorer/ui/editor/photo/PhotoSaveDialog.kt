package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PhotoSaveDialog(
    originalFileName: String,
    onDismiss: () -> Unit,
    onConfirmSave: (saveAsNew: Boolean, customFileName: String, qualityPercent: Float) -> Unit
) {
    var saveAsNewOption by remember { mutableStateOf(true) }
    var customFileName by remember { mutableStateOf("") }
    var qualityPercent by remember { mutableFloatStateOf(85f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Edited Photo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose how to save your edited image:", style = MaterialTheme.typography.bodySmall)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = saveAsNewOption, onClick = { saveAsNewOption = true })
                    Text("Save as New File (Default)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !saveAsNewOption, onClick = { saveAsNewOption = false })
                    Text("Replace Original File", style = MaterialTheme.typography.bodyMedium)
                }

                if (saveAsNewOption) {
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("New Filename (Optional)") },
                        placeholder = { Text("${originalFileName.substringBeforeLast('.')}_edited.${originalFileName.substringAfterLast('.', "jpg")}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quality: ${qualityPercent.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = qualityPercent,
                        onValueChange = { qualityPercent = it },
                        valueRange = 20f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSave(saveAsNewOption, customFileName, qualityPercent) }
            ) { Text("Save Image") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
