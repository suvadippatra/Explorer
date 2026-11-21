package com.troikoss.continuum_explorer.ui.components.hosting

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayRoomState
import com.troikoss.continuum_explorer.server.UnifiedServerState
import com.troikoss.continuum_explorer.server.omnios.OmniOSDiscoveryManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSDisplayProjectionManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSPairingManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSQrRole
import com.troikoss.continuum_explorer.server.omnios.OutboundInviteState
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider

/**
 * Ultra-Clean Tonal Flat Material 3 OmniOS Casting Workbench.
 *
 * Supports:
 * - Role switcher: **Host** | **Receiver**
 * - Live Device IP badge
 * - 5 Cast Modes: Built-In Screen, Web Cast, Wired Cast (USB-C / HDMI), Wireless Cast, Same App (LAN)
 * - Auto LAN NSD discovery + Manual IP + Two-Way QR (Show / Scan on both Host & Receiver)
 * - Receiver-side permission prompt for App-to-App Cast (no approval gate for Web/Wired/Wireless/Built-In)
 * - Live Host Admin Inspector (My Suite + Core apps, shared download files, display/audio calibration)
 * - SyncPlay inside Casting (locked fullscreen minimizable window inside OmniOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingCastingTab(
    isWideScreen: Boolean,
    unifiedState: UnifiedServerState,
    syncState: SyncPlayRoomState,
    liveSyncPosMs: Long,
    onShowQr: (title: String, qrContent: String, displayAddress: String) -> Unit,
    onLaunchBuiltInScreen: (url: String) -> Unit,
    onAddSharedFile: () -> Unit,
    onAddSharedFolder: () -> Unit,
    onPickSyncPlayMedia: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    // 0 = Host, 1 = Receiver
    var selectedRole by remember { mutableIntStateOf(0) }
    var showQrScanner by remember { mutableStateOf(false) }
    var mobileAdminExpanded by remember { mutableStateOf(false) }
    var manualReceiverInput by remember { mutableStateOf("") }
    var manualHostInput by remember { mutableStateOf("") }

    val discoveredHosts by OmniOSDiscoveryManager.discoveredHosts.collectAsState()
    val discoveredReceivers by OmniOSDiscoveryManager.discoveredReceivers.collectAsState()
    val isScanningHosts by OmniOSDiscoveryManager.isScanningHosts.collectAsState()
    val isScanningReceivers by OmniOSDiscoveryManager.isScanningReceivers.collectAsState()
    val externalDisplays by OmniOSDisplayProjectionManager.externalDisplays.collectAsState()
    val projectedDisplayId by OmniOSDisplayProjectionManager.activeProjectedDisplayId.collectAsState()
    val outboundInvite by OmniOSPairingManager.outboundInviteState.collectAsState()
    val pendingReceiverInvites by OmniOSPairingManager.pendingReceiverInvites.collectAsState()
    val receiverPort by OmniOSPairingManager.receiverListenerPort.collectAsState()
    val isReceiverListening by OmniOSPairingManager.isReceiverListening.collectAsState()

    val isCastingRunning = unifiedState.isCastingRunning
    val castPort = unifiedState.casting.port.takeIf { it > 0 } ?: ServerManager.DEFAULT_CASTING_PORT
    val deviceIp = unifiedState.ipAddress
    val webCastUrl = "http://$deviceIp:$castPort"

    // Manage NSD discovery & Receiver listener lifecycle per role
    LaunchedEffect(selectedRole) {
        OmniOSDisplayProjectionManager.refreshExternalDisplays()
        if (selectedRole == 0) {
            OmniOSDiscoveryManager.stopDiscoveringHosts()
            OmniOSPairingManager.stopReceiverListener()
            OmniOSDiscoveryManager.startDiscoveringReceivers(context)
        } else {
            OmniOSDiscoveryManager.stopDiscoveringReceivers()
            OmniOSPairingManager.startReceiverListener(context)
            OmniOSDiscoveryManager.startDiscoveringHosts(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            OmniOSDiscoveryManager.stopDiscoveringHosts()
            OmniOSDiscoveryManager.stopDiscoveringReceivers()
        }
    }

    // Receiver Permission Dialog (App-to-App Cast asks permission of the Receiver)
    val nextInvite = pendingReceiverInvites.firstOrNull()
    if (nextInvite != null) {
        ReceiverPermissionPromptDialog(
            invite = nextInvite,
            onAllow = { OmniOSPairingManager.acceptReceiverInvite(nextInvite.inviteId) },
            onDecline = { OmniOSPairingManager.declineReceiverInvite(nextInvite.inviteId) }
        )
    }

    // On-device Two-Way QR Scanner Modal
    if (showQrScanner) {
        val scanningAsHost = selectedRole == 0
        QrScannerModalDialog(
            title = if (scanningAsHost) "Scan Receiver QR" else "Scan Host QR",
            manualInputHint = if (scanningAsHost) "192.168.1.20:8091" else "192.168.1.10:8080",
            onScanned = { raw ->
                showQrScanner = false
                val parsed = OmniOSPairingManager.parseQrOrManualAddress(
                    rawInput = raw,
                    defaultRole = if (scanningAsHost) OmniOSQrRole.RECEIVER else OmniOSQrRole.HOST,
                    defaultPort = if (scanningAsHost) OmniOSPairingManager.DEFAULT_RECEIVER_LISTENER_PORT else ServerManager.DEFAULT_CASTING_PORT
                )
                if (parsed == null) {
                    Toast.makeText(context, "Invalid QR or address", Toast.LENGTH_SHORT).show()
                } else if (scanningAsHost) {
                    if (!isCastingRunning) {
                        ServerManager.startCastingServer(context, castPort)
                    }
                    OmniOSPairingManager.sendCastInviteToReceiver(
                        receiverIp = parsed.ip,
                        receiverPort = parsed.port,
                        receiverName = parsed.deviceName,
                        hostPort = castPort
                    )
                } else {
                    OmniOSPairingManager.requestLocalReceiverPermission(
                        hostIp = parsed.ip,
                        hostPort = parsed.port,
                        hostDeviceName = parsed.deviceName
                    )
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- ROLE SWITCHER + DEVICE IP HEADER STRIP ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {
                    SegmentedButton(
                        selected = selectedRole == 0,
                        onClick = { selectedRole = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("cast_role_host")
                    ) {
                        Text("Host", maxLines = 1)
                    }
                    SegmentedButton(
                        selected = selectedRole == 1,
                        onClick = { selectedRole = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("cast_role_receiver")
                    ) {
                        Text("Receiver", maxLines = 1)
                    }
                }

                Surface(
                    onClick = {
                        clipboard.setText(AnnotatedString(deviceIp))
                        Toast.makeText(context, "Copied IP: $deviceIp", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.testTag("cast_device_ip_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = deviceIp,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }

        if (selectedRole == 0) {
            // =====================================================================
            // HOST ROLE: 5 CAST MODES + SYNCPLAY + LIVE ADMIN INSPECTOR
            // =====================================================================
            val hostLeftPane: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Primary OmniOS Power & Quick QR Card
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
                                            .background(if (isCastingRunning) cs.primary else cs.outlineVariant)
                                    )
                                    Text(
                                        text = "OmniOS Host",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isCastingRunning) {
                                        Text(
                                            text = ":${castPort} • ${unifiedState.casting.activeConnections}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = cs.primary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCastingRunning) {
                                        IconButton(
                                            onClick = { ServerManager.restartCastingServer(context, castPort) },
                                            modifier = Modifier.testTag("cast_restart_button")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Restart OmniOS")
                                        }
                                    }
                                    Switch(
                                        checked = isCastingRunning,
                                        onCheckedChange = { enable ->
                                            if (enable) ServerManager.startCastingServer(context, castPort)
                                            else ServerManager.stopCastingServer(context, graceful = true)
                                        },
                                        modifier = Modifier.testTag("cast_power_switch")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (!isCastingRunning) {
                                            ServerManager.startCastingServer(context, castPort)
                                        }
                                        onShowQr(
                                            "Host QR",
                                            OmniOSPairingManager.buildHostQrPayload(castPort),
                                            webCastUrl
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cast_host_show_qr_button")
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Show QR", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { showQrScanner = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cast_host_scan_qr_button")
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan QR", maxLines = 1)
                                }
                            }
                        }
                    }

                    // 1. BUILT-IN SCREEN CAST (No permission needed)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = cs.primary)
                                Text(
                                    text = "Built-In Screen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    if (!isCastingRunning) {
                                        ServerManager.startCastingServer(context, castPort)
                                    }
                                    onLaunchBuiltInScreen("http://127.0.0.1:$castPort/?mode=builtin_screen")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("cast_builtin_now_button")
                            ) {
                                Text("Cast Now")
                            }
                        }
                    }

                    // 2. WEB CAST (No approval needed)
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = cs.primary)
                                Text(
                                    text = "Web Cast",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cs.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = webCastUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isCastingRunning) cs.primary else cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(webCastUrl))
                                            Toast.makeText(context, "Copied Web Cast URL", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("cast_web_copy_button")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Web Cast URL", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                            onShowQr("Web Cast QR", webCastUrl, webCastUrl)
                                        },
                                        modifier = Modifier.testTag("cast_web_qr_button")
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = "Web Cast QR", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webCastUrl)))
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.testTag("cast_web_open_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Web Cast", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 3. WIRED CAST (USB-C DisplayPort / HDMI) & 4. WIRELESS CAST (Miracast)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Usb, contentDescription = null, tint = cs.primary)
                                    Text(
                                        text = "Wired Cast",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { OmniOSDisplayProjectionManager.refreshExternalDisplays() },
                                    modifier = Modifier.testTag("cast_wired_refresh_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Scan external displays", modifier = Modifier.size(18.dp))
                                }
                            }

                            if (externalDisplays.isEmpty()) {
                                val adbCmd = OmniOSDisplayProjectionManager.getAdbReverseCommand(castPort)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = cs.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = adbCmd,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(adbCmd))
                                                Toast.makeText(context, "Copied USB command", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.testTag("cast_adb_copy_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy ADB reverse", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            } else {
                                externalDisplays.forEach { disp ->
                                    val isProjected = projectedDisplayId == disp.displayId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cs.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = disp.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${disp.width}×${disp.height} • ${disp.refreshRateHz.toInt()}Hz",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = cs.onSurfaceVariant
                                                )
                                            }
                                            if (isProjected) {
                                                Button(
                                                    onClick = { OmniOSDisplayProjectionManager.stopWiredProjection() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = cs.error),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(44.dp)
                                                ) {
                                                    Text("Stop")
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = {
                                                        if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                                        OmniOSDisplayProjectionManager.startWiredProjection(context, disp.displayId, castPort)
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(44.dp)
                                                ) {
                                                    Text("Project")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = cs.primary)
                                    Text(
                                        text = "Wireless Cast",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                        val launched = OmniOSDisplayProjectionManager.openWirelessDisplaySettings(context)
                                        if (!launched) {
                                            Toast.makeText(context, "Wireless Display settings unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("cast_wireless_open_button")
                                ) {
                                    Text("Open Cast")
                                }
                            }
                        }
                    }

                    // 5. SAME APP ON SAME NETWORK (LAN Receivers + Manual IP + Receiver Permission)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = cs.primary)
                                    Text(
                                        text = "Same App (LAN)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        OmniOSDiscoveryManager.refreshDiscovery(context, scanHosts = false, scanReceivers = true)
                                    },
                                    modifier = Modifier.testTag("cast_lan_refresh_receivers")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh LAN receivers", modifier = Modifier.size(18.dp))
                                }
                            }

                            // Live outbound invite banner
                            OutboundInviteStatusBanner(
                                state = outboundInvite,
                                onDismiss = { OmniOSPairingManager.resetOutboundInviteState() }
                            )

                            if (discoveredReceivers.isEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isScanningReceivers) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    }
                                    Text(
                                        text = "Searching LAN receivers...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            } else {
                                discoveredReceivers.forEach { rx ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cs.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = rx.deviceName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${rx.ip}:${rx.port}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = cs.onSurfaceVariant
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                                    OmniOSPairingManager.sendCastInviteToReceiver(
                                                        receiverIp = rx.ip,
                                                        receiverPort = rx.port,
                                                        receiverName = rx.deviceName,
                                                        hostPort = castPort
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(44.dp)
                                            ) {
                                                Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Cast")
                                            }
                                        }
                                    }
                                }
                            }

                            // Manual Receiver IP input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualReceiverInput,
                                    onValueChange = { manualReceiverInput = it },
                                    singleLine = true,
                                    placeholder = { Text("Receiver IP:8091", fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cast_manual_receiver_ip")
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val parsed = OmniOSPairingManager.parseQrOrManualAddress(
                                            rawInput = manualReceiverInput,
                                            defaultRole = OmniOSQrRole.RECEIVER,
                                            defaultPort = OmniOSPairingManager.DEFAULT_RECEIVER_LISTENER_PORT
                                        )
                                        if (parsed != null) {
                                            if (!isCastingRunning) ServerManager.startCastingServer(context, castPort)
                                            OmniOSPairingManager.sendCastInviteToReceiver(
                                                receiverIp = parsed.ip,
                                                receiverPort = parsed.port,
                                                receiverName = parsed.deviceName,
                                                hostPort = castPort
                                            )
                                        } else {
                                            Toast.makeText(context, "Enter valid IP", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = manualReceiverInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("cast_send_manual_invite_button")
                                ) {
                                    Text("Cast")
                                }
                            }
                        }
                    }

                    // SYNCPLAY IN CASTING (Shared Engine, Locked Minimizable Window)
                    SyncPlayControlCard(
                        syncState = syncState,
                        livePositionMs = liveSyncPosMs,
                        syncPlayWebUrl = webCastUrl,
                        isCastingContext = true,
                        onPickMediaFile = onPickSyncPlayMedia,
                        onShowSyncQr = { onShowQr("OmniOS Cast QR", webCastUrl, webCastUrl) }
                    )
                }
            }

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1.15f)) { hostLeftPane() }
                    TonalVerticalPaneDivider()
                    Box(modifier = Modifier.weight(0.85f)) {
                        HostingAdminInspector(
                            onAddSharedFile = onAddSharedFile,
                            onAddSharedFolder = onAddSharedFolder
                        )
                    }
                }
            } else {
                hostLeftPane()

                // Collapsible Mobile Admin Console
                Surface(
                    onClick = { mobileAdminExpanded = !mobileAdminExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cast_mobile_admin_toggle")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Admin Console",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (mobileAdminExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Admin Console"
                        )
                    }
                }

                AnimatedVisibility(visible = mobileAdminExpanded) {
                    HostingAdminInspector(
                        onAddSharedFile = onAddSharedFile,
                        onAddSharedFolder = onAddSharedFolder
                    )
                }
            }
        } else {
            // =====================================================================
            // RECEIVER ROLE: SHOW MY QR / SCAN HOST QR / NSD HOSTS / MANUAL IP
            // =====================================================================
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
                                    .background(if (isReceiverListening) cs.primary else cs.outlineVariant)
                            )
                            Text(
                                text = "Receiver Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "$deviceIp:$receiverPort",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onShowQr(
                                    "Receiver QR",
                                    OmniOSPairingManager.buildReceiverQrPayload(),
                                    "$deviceIp:$receiverPort"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("receiver_show_qr_button")
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show My QR", maxLines = 1)
                        }
                        Button(
                            onClick = { showQrScanner = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("receiver_scan_host_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Host QR", maxLines = 1)
                        }
                    }
                }
            }

            // Discovered LAN OmniOS Hosts
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
                            text = "Nearby Hosts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                OmniOSDiscoveryManager.refreshDiscovery(context, scanHosts = true, scanReceivers = false)
                            },
                            modifier = Modifier.testTag("receiver_refresh_hosts_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh hosts", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (discoveredHosts.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isScanningHosts) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                            Text(
                                text = "Searching LAN hosts...",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    } else {
                        discoveredHosts.forEach { host ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cs.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = cs.primary)
                                        Column {
                                            Text(
                                                text = host.deviceName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${host.ip}:${host.port}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = cs.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            OmniOSPairingManager.requestLocalReceiverPermission(
                                                hostIp = host.ip,
                                                hostPort = host.port,
                                                hostDeviceName = host.deviceName
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

                    // Manual Host IP Entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualHostInput,
                            onValueChange = { manualHostInput = it },
                            singleLine = true,
                            placeholder = { Text("Host IP:8080", fontFamily = FontFamily.Monospace) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receiver_manual_host_ip")
                        )
                        Button(
                            onClick = {
                                val parsed = OmniOSPairingManager.parseQrOrManualAddress(
                                    rawInput = manualHostInput,
                                    defaultRole = OmniOSQrRole.HOST,
                                    defaultPort = ServerManager.DEFAULT_CASTING_PORT
                                )
                                if (parsed != null) {
                                    OmniOSPairingManager.requestLocalReceiverPermission(
                                        hostIp = parsed.ip,
                                        hostPort = parsed.port,
                                        hostDeviceName = parsed.deviceName
                                    )
                                } else {
                                    Toast.makeText(context, "Enter valid IP", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = manualHostInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("receiver_manual_connect_button")
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboundInviteStatusBanner(
    state: OutboundInviteState,
    onDismiss: () -> Unit
) {
    if (state is OutboundInviteState.Idle) return
    val cs = MaterialTheme.colorScheme
    val (bg, fg, label, showSpinner) = when (state) {
        is OutboundInviteState.Sending -> Quad(cs.secondaryContainer, cs.onSecondaryContainer, "Contacting ${state.targetName}...", true)
        is OutboundInviteState.WaitingForReceiver -> Quad(cs.primaryContainer, cs.onPrimaryContainer, "Waiting for ${state.targetName} to allow...", true)
        is OutboundInviteState.Accepted -> Quad(cs.primaryContainer, cs.onPrimaryContainer, "${state.targetName} connected", false)
        is OutboundInviteState.Declined -> Quad(cs.errorContainer, cs.onErrorContainer, "${state.targetName} declined", false)
        is OutboundInviteState.Failed -> Quad(cs.errorContainer, cs.onErrorContainer, state.message, false)
        OutboundInviteState.Idle -> return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
            } else {
                Icon(Icons.Default.CastConnected, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
