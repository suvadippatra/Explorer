package com.troikoss.continuum_explorer.ui.components.hosting

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.troikoss.continuum_explorer.server.omnios.IncomingCastInvite
import com.troikoss.continuum_explorer.utils.QrCodeView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-Clean Tonal Flat Material 3 QR Popup Dialog for Host QR, Receiver QR, Web Cast QR,
 * FTP QR, and HTTP QR sharing.
 */
@Composable
fun QrDisplayPopupDialog(
    title: String,
    qrContent: String,
    displayAddress: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("qr_popup_close_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close QR popup",
                            tint = cs.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainerLowest
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        QrCodeView(
                            content = qrContent,
                            size = 224.dp
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(displayAddress))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("qr_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy address",
                                tint = cs.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("qr_close_button")
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * On-device CameraX + ML Kit QR Scanner Modal with instant manual IP/address fallback.
 * Requests `CAMERA` permission at runtime only when opened.
 */
@Composable
fun QrScannerModalDialog(
    title: String,
    manualInputHint: String = "192.168.1.10:8080",
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var manualAddress by remember { mutableStateOf("") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("qr_scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close scanner",
                            tint = cs.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraQrPreviewSurface(
                            onCodeDetected = { raw ->
                                onScanned(raw)
                            }
                        )
                        ScannerCornerReticle(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(36.dp)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Camera unavailable",
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (permissionDenied) "Camera access denied" else "Camera permission required",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("qr_grant_camera_button")
                            ) {
                                Text("Enable Camera")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = { manualAddress = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = manualInputHint,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_manual_ip_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = manualAddress.trim()
                            if (trimmed.isNotEmpty()) {
                                onScanned(trimmed)
                            }
                        },
                        enabled = manualAddress.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("qr_manual_connect_button")
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

/**
 * Receiver-side App-to-App Cast permission prompt shown when a Host requests to cast
 * or when Receiver initiates a session.
 */
@Composable
fun ReceiverPermissionPromptDialog(
    invite: IncomingCastInvite,
    onAllow: () -> Unit,
    onDecline: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Incoming cast request",
                                tint = cs.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow OmniOS Cast",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "${invite.hostDeviceName} • ${invite.hostIp}:${invite.hostPort}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("receiver_decline_button")
                    ) {
                        Text("Decline")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onAllow,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("receiver_allow_button")
                    ) {
                        Text("Allow")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraQrPreviewSurface(
    onCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scannedOnce = remember { AtomicBoolean(false) }
    val isDisposed = remember { AtomicBoolean(false) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            try {
                boundCameraProvider?.unbindAll()
            } catch (_: Exception) {}
            boundCameraProvider = null
            try {
                scanner.close()
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                if (isDisposed.get()) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    if (isDisposed.get()) return@addListener
                    boundCameraProvider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !scannedOnce.get() && !isDisposed.get()) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                            if (raw != null && !isDisposed.get() && scannedOnce.compareAndSet(false, true)) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    onCodeDetected(raw)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                    // Fallback manual input remains visible below
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
private fun ScannerCornerReticle(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val cornerLen = 28.dp.toPx()
        val w = size.width
        val h = size.height

        // Top-left
        drawLine(accent, Offset(0f, 0f), Offset(cornerLen, 0f), stroke, StrokeCap.Round)
        drawLine(accent, Offset(0f, 0f), Offset(0f, cornerLen), stroke, StrokeCap.Round)
        // Top-right
        drawLine(accent, Offset(w, 0f), Offset(w - cornerLen, 0f), stroke, StrokeCap.Round)
        drawLine(accent, Offset(w, 0f), Offset(w, cornerLen), stroke, StrokeCap.Round)
        // Bottom-left
        drawLine(accent, Offset(0f, h), Offset(cornerLen, h), stroke, StrokeCap.Round)
        drawLine(accent, Offset(0f, h), Offset(0f, h - cornerLen), stroke, StrokeCap.Round)
        // Bottom-right
        drawLine(accent, Offset(w, h), Offset(w - cornerLen, h), stroke, StrokeCap.Round)
        drawLine(accent, Offset(w, h), Offset(w, h - cornerLen), stroke, StrokeCap.Round)
    }
}
