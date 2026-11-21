package com.troikoss.continuum_explorer.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * ISO/IEC 18004 standard QR Code generator & Compose Canvas renderer backed by ZXing Core.
 * Generates scannable QR matrices with Reed-Solomon Level-M error correction for URLs and
 * two-way OmniOS pairing JSON payloads.
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    val qrMatrix = remember(content) {
        SimpleQrEncoder.encode(content)
    }

    val matrixSize = qrMatrix.size

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(lightColor)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (matrixSize <= 0) return@Canvas
            val cellSize = this.size.width / matrixSize.toFloat()

            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    if (qrMatrix[row][col]) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize + 0.5f, cellSize + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard QR Code Matrix Encoder backed by ZXing [QRCodeWriter].
 */
object SimpleQrEncoder {

    fun encode(data: String): Array<BooleanArray> {
        val safeContent = data.ifBlank { "omnios" }
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 0
            )
            val bitMatrix = QRCodeWriter().encode(
                safeContent,
                BarcodeFormat.QR_CODE,
                0,
                0,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            Array(height) { row ->
                BooleanArray(width) { col -> bitMatrix.get(col, row) }
            }
        } catch (_: Exception) {
            Array(21) { BooleanArray(21) }
        }
    }
}
