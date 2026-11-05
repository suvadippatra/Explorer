package com.troikoss.continuum_explorer.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.GlobalEvents
import java.io.File

@Composable
fun BatchRenameDialog(
    selectedFiles: List<UniversalFile>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var startNumberInput by remember { mutableStateOf("1") }
    var useNumbering by remember { mutableStateOf(true) }
    var numberPaddingInput by remember { mutableStateOf("3") }

    val startNum = startNumberInput.toIntOrNull() ?: 1
    val padding = (numberPaddingInput.toIntOrNull() ?: 1).coerceIn(1, 6)

    // Compute preview list
    val previewList = remember(selectedFiles, prefix, suffix, useNumbering, startNum, padding) {
        selectedFiles.mapIndexed { index, file ->
            val ext = if (file.isDirectory) "" else file.name.substringAfterLast('.', "")
            val extSuffix = if (ext.isNotEmpty() && ext != file.name) ".$ext" else ""
            val baseNameWithoutExt = if (file.isDirectory) file.name else file.name.substringBeforeLast('.')
            
            val numStr = if (useNumbering) {
                val num = startNum + index
                String.format("%0${padding}d", num)
            } else ""

            val newName = if (useNumbering) {
                "$prefix$numStr$suffix$extSuffix"
            } else {
                "$prefix$baseNameWithoutExt$suffix$extSuffix"
            }
            file.name to newName
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Batch Rename (${selectedFiles.size} items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Controls Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Prefix", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("e.g. Photo_") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text("Suffix", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("e.g. _vacation") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = useNumbering,
                        onCheckedChange = { useNumbering = it }
                    )
                    Text("Sequential Numbering", style = MaterialTheme.typography.bodyMedium)
                }

                if (useNumbering) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startNumberInput,
                            onValueChange = { startNumberInput = it },
                            label = { Text("Start At", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = numberPaddingInput,
                            onValueChange = { numberPaddingInput = it },
                            label = { Text("Digits Padding", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("3 -> 001") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = "Live Rename Preview",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Preview list
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(previewList) { index, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pair.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("➔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = pair.second,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            var renamedCount = 0
                            for (pair in previewList) {
                                val originalFile = selectedFiles.find { it.name == pair.first }?.fileRef
                                if (originalFile != null && originalFile.exists()) {
                                    val parent = originalFile.parentFile
                                    if (parent != null) {
                                        val target = File(parent, pair.second)
                                        if (originalFile != target && !target.exists()) {
                                            if (originalFile.renameTo(target)) {
                                                renamedCount++
                                            }
                                        }
                                    }
                                }
                            }
                            Toast.makeText(context, "Batch renamed $renamedCount files successfully", Toast.LENGTH_SHORT).show()
                            GlobalEvents.triggerRefresh()
                            onSuccess()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply Batch Rename")
                    }
                }
            }
        }
    }
}
