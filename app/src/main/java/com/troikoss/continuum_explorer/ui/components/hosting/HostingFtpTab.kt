package com.troikoss.continuum_explorer.ui.components.hosting

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.server.SyncPlayRoomState
import com.troikoss.continuum_explorer.server.UnifiedServerState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ultra-Clean Tonal Flat Material 3 FTP Hosting Workbench.
 * Includes exclusive FTP Server controls, Shared Scope, Credentials, Uploads, and SyncPlay Party.
 */
@Composable
fun HostingFtpTab(
    isWideScreen: Boolean,
    unifiedState: UnifiedServerState,
    syncState: SyncPlayRoomState,
    liveSyncPosMs: Long,
    ftpUrl: String,
    syncPlayWebUrl: String,
    screenShareWebUrl: String,
    hostedMode: FtpHostedMode,
    portInput: String,
    isAnonymousAccess: Boolean,
    usernameInput: String,
    passwordInput: String,
    allowUploads: Boolean,
    uploadPasswordInput: String,
    uploadFolderPath: String,
    onToggleFtp: (Boolean) -> Unit,
    onRestartFtp: () -> Unit,
    onShowQr: (title: String, content: String, label: String) -> Unit,
    onScopeChange: (FtpHostedMode) -> Unit,
    onPickSharedItems: () -> Unit,
    onPortChange: (String) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onApplyNetwork: () -> Unit,
    onAllowUploadsChange: (Boolean) -> Unit,
    onUploadPasswordChange: (String) -> Unit,
    onSelectUploadFolder: () -> Unit,
    onPickSyncPlayMedia: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit
) {
    val leftPane: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FtpCoreControlCard(
                unifiedState = unifiedState,
                ftpUrl = ftpUrl,
                hostedMode = hostedMode,
                portInput = portInput,
                isAnonymousAccess = isAnonymousAccess,
                usernameInput = usernameInput,
                passwordInput = passwordInput,
                onToggleFtp = onToggleFtp,
                onRestartFtp = onRestartFtp,
                onShowQr = { onShowQr("FTP QR", ftpUrl, ftpUrl) },
                onScopeChange = onScopeChange,
                onPickSharedItems = onPickSharedItems,
                onPortChange = onPortChange,
                onAnonymousChange = onAnonymousChange,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onApplyNetwork = onApplyNetwork
            )

            FtpUploadsCard(
                allowUploads = allowUploads,
                uploadPasswordInput = uploadPasswordInput,
                uploadFolderPath = uploadFolderPath,
                onAllowUploadsChange = onAllowUploadsChange,
                onUploadPasswordChange = onUploadPasswordChange,
                onSelectUploadFolder = onSelectUploadFolder
            )
        }
    }

    val rightPane: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FtpScreenShareCard(
                isScreenCasting = unifiedState.isScreenCasting,
                screenShareWebUrl = screenShareWebUrl,
                onStartScreenShare = onStartScreenShare,
                onStopScreenShare = onStopScreenShare,
                onShowQr = { onShowQr("Screen Share QR", screenShareWebUrl, screenShareWebUrl) }
            )

            SyncPlayControlCard(
                syncState = syncState,
                livePositionMs = liveSyncPosMs,
                syncPlayWebUrl = syncPlayWebUrl,
                isCastingContext = false,
                onPickMediaFile = onPickSyncPlayMedia,
                onShowSyncQr = { onShowQr("SyncPlay QR", syncPlayWebUrl, syncPlayWebUrl) }
            )
        }
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1.1f)) { leftPane() }
            Box(modifier = Modifier.weight(0.9f)) { rightPane() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leftPane()
            rightPane()
        }
    }
}

@Composable
private fun FtpCoreControlCard(
    unifiedState: UnifiedServerState,
    ftpUrl: String,
    hostedMode: FtpHostedMode,
    portInput: String,
    isAnonymousAccess: Boolean,
    usernameInput: String,
    passwordInput: String,
    onToggleFtp: (Boolean) -> Unit,
    onRestartFtp: () -> Unit,
    onShowQr: () -> Unit,
    onScopeChange: (FtpHostedMode) -> Unit,
    onPickSharedItems: () -> Unit,
    onPortChange: (String) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onApplyNetwork: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    var showPassword by remember { mutableStateOf(false) }
    val isRunning = unifiedState.isFtpRunning
    val bucketCount = FtpBucketManager.bucketItems.size

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) cs.primary else cs.outlineVariant)
                    )
                    Text(
                        text = "FTP Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    if (isRunning) {
                        Text(
                            text = ":${unifiedState.ftp.port}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        IconButton(
                            onClick = onRestartFtp,
                            modifier = Modifier.testTag("ftp_restart_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart FTP", tint = cs.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = onToggleFtp,
                        modifier = Modifier.testTag("ftp_power_switch")
                    )
                }
            }

            // Live FTP Address Strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ftpUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRunning) cs.primary else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(ftpUrl))
                            Toast.makeText(context, "Copied FTP URL", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("ftp_copy_url_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy FTP URL", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onShowQr,
                        modifier = Modifier.testTag("ftp_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Show FTP QR", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Shared Scope Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = hostedMode == FtpHostedMode.BATCH_FILES,
                    onClick = { onScopeChange(FtpHostedMode.BATCH_FILES) },
                    label = { Text("Selected ($bucketCount)") },
                    modifier = Modifier.testTag("ftp_scope_selected")
                )
                FilterChip(
                    selected = hostedMode == FtpHostedMode.FULL_STORAGE,
                    onClick = { onScopeChange(FtpHostedMode.FULL_STORAGE) },
                    label = { Text("Full Storage") },
                    modifier = Modifier.testTag("ftp_scope_full")
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onPickSharedItems,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ftp_pick_files_button")
                ) {
                    Text("Pick")
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

            // Port & Auth Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { onPortChange(it.filter { ch -> ch.isDigit() }.take(5)) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(116.dp)
                        .testTag("ftp_port_input")
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Anonymous", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isAnonymousAccess,
                        onCheckedChange = onAnonymousChange,
                        modifier = Modifier.testTag("ftp_anonymous_switch")
                    )
                }
            }

            if (!isAnonymousAccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = onUsernameChange,
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ftp_username_input")
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ftp_password_input")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = onApplyNetwork,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ftp_apply_button")
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun FtpUploadsCard(
    allowUploads: Boolean,
    uploadPasswordInput: String,
    uploadFolderPath: String,
    onAllowUploadsChange: (Boolean) -> Unit,
    onUploadPasswordChange: (String) -> Unit,
    onSelectUploadFolder: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var showPass by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "File Uploads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Switch(
                    checked = allowUploads,
                    onCheckedChange = onAllowUploadsChange,
                    modifier = Modifier.testTag("ftp_uploads_switch")
                )
            }

            if (allowUploads) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uploadFolderPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onSelectUploadFolder,
                            modifier = Modifier.testTag("ftp_upload_folder_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Folder")
                        }
                    }
                }

                OutlinedTextField(
                    value = uploadPasswordInput,
                    onValueChange = onUploadPasswordChange,
                    label = { Text("Upload Password") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle upload password",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ftp_upload_password_input")
                )
            }
        }
    }
}

@Composable
private fun FtpScreenShareCard(
    isScreenCasting: Boolean,
    screenShareWebUrl: String,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onShowQr: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    var showTuning by remember { mutableStateOf(false) }
    val resolution by SettingsManager.screenCastResolution
    val fps by SettingsManager.screenCastFps
    val quality by SettingsManager.screenCastQuality
    val rotation by SettingsManager.screenCastRotation
    val audio by SettingsManager.screenCastAudio

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isScreenCasting) cs.primary else cs.outlineVariant)
                    )
                    Text(
                        text = "Screen Share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isScreenCasting) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showTuning = !showTuning },
                        modifier = Modifier.testTag("ftp_screen_share_tune_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Screen Share Tuning",
                            tint = if (showTuning) cs.primary else cs.onSurfaceVariant
                        )
                    }
                    if (isScreenCasting) {
                        FilledTonalButton(
                            onClick = onStopScreenShare,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = cs.errorContainer,
                                contentColor = cs.onErrorContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ftp_screen_share_button")
                        ) {
                            Icon(Icons.Default.StopScreenShare, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = onStartScreenShare,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ftp_screen_share_button")
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }

            if (isScreenCasting) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = screenShareWebUrl,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(screenShareWebUrl))
                                Toast.makeText(context, "Copied Screen Share URL", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("ftp_screen_share_copy_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Screen Share URL", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onShowQr,
                            modifier = Modifier.testTag("ftp_screen_share_qr_button")
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Screen Share QR", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(screenShareWebUrl)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.testTag("ftp_screen_share_open_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Screen Share", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (showTuning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("480p", "720p", "1080p").forEach { res ->
                                FilterChip(
                                    selected = resolution == res,
                                    onClick = {
                                        SettingsManager.setScreenCastParams(context, res, fps, rotation, audio, quality)
                                    },
                                    label = { Text(res) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 90, 180, 270).forEach { deg ->
                                FilterChip(
                                    selected = rotation == deg,
                                    onClick = {
                                        SettingsManager.setScreenCastParams(context, resolution, fps, deg, audio, quality)
                                    },
                                    label = { Text("${deg}°") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("FPS", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "$fps fps",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.primary
                                )
                            }
                            Slider(
                                value = fps.toFloat(),
                                onValueChange = {
                                    SettingsManager.setScreenCastParams(context, resolution, it.roundToInt(), rotation, audio, quality)
                                },
                                valueRange = 5f..30f
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quality", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "$quality%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.primary
                                )
                            }
                            Slider(
                                value = quality.toFloat(),
                                onValueChange = {
                                    SettingsManager.setScreenCastParams(context, resolution, fps, rotation, audio, it.roundToInt())
                                },
                                valueRange = 30f..95f
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared Ultra-Clean Tonal M3 SyncPlay Party Card reused by both FTP Hosting and Casting tabs.
 */
@Composable
fun SyncPlayControlCard(
    syncState: SyncPlayRoomState,
    livePositionMs: Long,
    syncPlayWebUrl: String,
    isCastingContext: Boolean,
    onPickMediaFile: () -> Unit,
    onShowSyncQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    var showSettings by remember { mutableStateOf(false) }
    var pinInput by remember(syncState.passcode) { mutableStateOf(syncState.passcode) }
    var guestControl by remember(syncState.allowGuestControl) { mutableStateOf(syncState.allowGuestControl) }
    var syncHostAudio by remember(syncState.syncHostAudioPlayer) { mutableStateOf(syncState.syncHostAudioPlayer) }
    var leadTimeMs by remember(syncState.scheduleLeadTimeMs) { mutableFloatStateOf(syncState.scheduleLeadTimeMs.toFloat()) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (syncState.isActive) cs.primary else cs.outlineVariant)
                    )
                    Text(
                        text = if (isCastingContext) "SyncPlay Cast" else "SyncPlay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (syncState.isActive) {
                        Text(
                            text = "${syncState.connectedPeers.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showSettings = !showSettings },
                        modifier = Modifier.testTag("syncplay_security_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "SyncPlay Settings",
                            tint = if (showSettings) cs.primary else cs.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onPickMediaFile,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("syncplay_pick_media_button")
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (syncState.isActive) "Change" else "Media")
                    }
                }
            }

            // Room PIN strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = cs.primary, modifier = Modifier.size(15.dp))
                        Text(
                            text = "PIN ${syncState.passcode}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(syncState.passcode))
                                Toast.makeText(context, "Copied PIN", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("syncplay_copy_pin_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                val nextPin = SyncPlayManager.generatePin()
                                pinInput = nextPin
                                SyncPlayManager.updateRoomSecurity(nextPin, guestControl, leadTimeMs.toLong(), syncHostAudio)
                            },
                            modifier = Modifier.testTag("syncplay_regen_pin_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "New PIN", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (showSettings) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                if (it.isNotBlank()) {
                                    SyncPlayManager.updateRoomSecurity(it, guestControl, leadTimeMs.toLong(), syncHostAudio)
                                }
                            },
                            label = { Text("Room PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Guest Control", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = guestControl,
                                onCheckedChange = {
                                    guestControl = it
                                    SyncPlayManager.updateRoomSecurity(pinInput, it, leadTimeMs.toLong(), syncHostAudio)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Host Audio", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = syncHostAudio,
                                onCheckedChange = {
                                    syncHostAudio = it
                                    SyncPlayManager.updateRoomSecurity(pinInput, guestControl, leadTimeMs.toLong(), it)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lead Window", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${leadTimeMs.roundToInt()} ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = cs.primary
                            )
                        }
                        Slider(
                            value = leadTimeMs,
                            onValueChange = {
                                leadTimeMs = it
                                SyncPlayManager.updateRoomSecurity(pinInput, guestControl, it.toLong(), syncHostAudio)
                            },
                            valueRange = 20f..350f
                        )
                    }
                }
            }

            if (syncState.isActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = syncState.mediaFileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val mins = (livePositionMs / 1000L) / 60L
                                val secs = (livePositionMs / 1000L) % 60L
                                Text(
                                    text = "${if (syncState.isPlaying) "PLAY" else "PAUSE"} • ${String.format(Locale.US, "%02d:%02d", mins, secs)} • ${syncState.playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (isCastingContext) SyncPlayManager.stopSession(clearMediaHistory = true)
                                    else SyncPlayManager.stopSyncPlayAndServer(context)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = cs.errorContainer,
                                    contentColor = cs.onErrorContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("syncplay_stop_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    SyncPlayManager.triggerScheduledCommand(
                                        context = context,
                                        action = if (syncState.isPlaying) "PAUSE" else "PLAY",
                                        actorName = "Host"
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("syncplay_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (syncState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (syncState.isPlaying) "Pause" else "Play", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val t = (syncState.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                                    SyncPlayManager.triggerScheduledCommand(context, "SEEK", targetPositionMs = t, actorName = "Host")
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(48.dp)
                                    .testTag("syncplay_seek_back_button")
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "-10s", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalButton(
                                onClick = {
                                    val t = syncState.currentPositionMs() + 10_000L
                                    SyncPlayManager.triggerScheduledCommand(context, "SEEK", targetPositionMs = t, actorName = "Host")
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(48.dp)
                                    .testTag("syncplay_seek_fwd_button")
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "+10s", modifier = Modifier.size(18.dp))
                            }
                            OutlinedButton(
                                onClick = { SyncPlayManager.stopSession(clearMediaHistory = true) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(48.dp)
                                    .testTag("syncplay_unload_button")
                            ) {
                                Text("Unload", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                val selected = syncState.playbackSpeed == spd
                                Surface(
                                    onClick = {
                                        SyncPlayManager.triggerScheduledCommand(context, "SPEED", speed = spd, actorName = "Host")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${spd}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (!isCastingContext) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = syncPlayWebUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(onClick = onShowSyncQr) {
                                        Icon(Icons.Default.QrCode2, contentDescription = "SyncPlay QR", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString("$syncPlayWebUrl (PIN: ${syncState.passcode})"))
                                        Toast.makeText(context, "Copied SyncPlay URL", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy SyncPlay URL", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(syncPlayWebUrl)))
                                        } catch (_: Exception) {}
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open SyncPlay", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
