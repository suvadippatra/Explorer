package com.troikoss.continuum_explorer.archive.base64

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Archived Base64 & Hex Codec Utility.
 * Moved out of active My Suite & EditorContainer UI per user request so it remains preserved
 * in the repository for future reuse without cluttering the workspace.
 */
object ArchivedBase64Codec {

    fun encodeUtf8ToBase64(input: String): String {
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeBase64ToUtf8(input: String): String {
        return String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
    }

    @Composable
    fun ArchivedBase64CodecScreen() {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        var rawInput by remember { mutableStateOf("") }
        var encodedOutput by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Base64 & Hex Codec (Archived Module)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                label = { Text("Input Text or Base64") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        encodedOutput = try {
                            encodeUtf8ToBase64(rawInput)
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Encode Base64")
                }
                OutlinedButton(
                    onClick = {
                        encodedOutput = try {
                            decodeBase64ToUtf8(rawInput)
                        } catch (e: Exception) {
                            "Invalid Base64: ${e.message}"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decode Base64")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(encodedOutput))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy")
                }
            }
            OutlinedTextField(
                value = encodedOutput,
                onValueChange = {},
                readOnly = true,
                label = { Text("Output") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
