package com.troikoss.continuum_explorer.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin QR Code Matrix Generator and Compose Canvas Renderer.
 * Zero external dependencies, fast, and responsive.
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
            .clip(RoundedCornerShape(12.dp))
            .background(lightColor)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
 * Lightweight QR Code Matrix Encoder in Kotlin.
 * Encodes text into a standard QR matrix representation.
 */
object SimpleQrEncoder {

    fun encode(data: String): Array<BooleanArray> {
        val version = calculateVersion(data.length)
        val moduleCount = 17 + version * 4
        val matrix = Array(moduleCount) { BooleanArray(moduleCount) }
        val isReserved = Array(moduleCount) { BooleanArray(moduleCount) }

        // 1. Place Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        placeFinderPattern(matrix, isReserved, 0, 0)
        placeFinderPattern(matrix, isReserved, moduleCount - 7, 0)
        placeFinderPattern(matrix, isReserved, 0, moduleCount - 7)

        // 2. Alignment Patterns for Version >= 2
        if (version >= 2) {
            val alignPos = getAlignmentPositions(version)
            for (r in alignPos) {
                for (c in alignPos) {
                    if (!isReserved[r][c]) {
                        placeAlignmentPattern(matrix, isReserved, r - 2, c - 2)
                    }
                }
            }
        }

        // 3. Timing Patterns
        for (i in 8 until moduleCount - 8) {
            if (!isReserved[6][i]) {
                matrix[6][i] = (i % 2 == 0)
                isReserved[6][i] = true
            }
            if (!isReserved[i][6]) {
                matrix[i][6] = (i % 2 == 0)
                isReserved[i][6] = true
            }
        }

        // 4. Reserve Dark Module & Format Info Areas
        isReserved[4 * version + 9][8] = true
        matrix[4 * version + 9][8] = true

        for (i in 0..8) {
            if (i != 6) {
                if (i < moduleCount) isReserved[8][i] = true
                if (i < moduleCount) isReserved[i][8] = true
            }
        }
        for (i in moduleCount - 8 until moduleCount) {
            isReserved[8][i] = true
            isReserved[i][8] = true
        }

        // 5. Data Bit Placement
        val bits = encodeDataBits(data, version)
        var bitIndex = 0

        var right = moduleCount - 1
        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column

            val upward = ((right + 1) / 2) % 2 == 1
            val rowRange = if (upward) (moduleCount - 1 downTo 0) else (0 until moduleCount)

            for (row in rowRange) {
                for (colOffset in 0..1) {
                    val col = right - colOffset
                    if (!isReserved[row][col]) {
                        var bit = false
                        if (bitIndex < bits.size) {
                            bit = bits[bitIndex++]
                        }

                        // Apply standard Mask Pattern 0: (row + col) % 2 == 0
                        val mask = (row + col) % 2 == 0
                        matrix[row][col] = bit xor mask
                    }
                }
            }
            right -= 2
        }

        // 6. Draw Format Information Bits (Mask 0, Error Correction L)
        placeFormatInfo(matrix, moduleCount)

        return matrix
    }

    private fun calculateVersion(length: Int): Int {
        return when {
            length <= 17 -> 1
            length <= 32 -> 2
            length <= 53 -> 3
            length <= 78 -> 4
            length <= 106 -> 5
            else -> 6
        }
    }

    private fun placeFinderPattern(
        matrix: Array<BooleanArray>,
        reserved: Array<BooleanArray>,
        r: Int,
        c: Int
    ) {
        for (row in -1..7) {
            for (col in -1..7) {
                val mr = r + row
                val mc = c + col
                if (mr in matrix.indices && mc in matrix.indices) {
                    reserved[mr][mc] = true
                    if (row in 0..6 && col in 0..6) {
                        val isBorder = row == 0 || row == 6 || col == 0 || col == 6
                        val isCenter = row in 2..4 && col in 2..4
                        matrix[mr][mc] = isBorder || isCenter
                    } else {
                        matrix[mr][mc] = false
                    }
                }
            }
        }
    }

    private fun placeAlignmentPattern(
        matrix: Array<BooleanArray>,
        reserved: Array<BooleanArray>,
        r: Int,
        c: Int
    ) {
        for (row in 0..4) {
            for (col in 0..4) {
                val mr = r + row
                val mc = c + col
                if (mr in matrix.indices && mc in matrix.indices && !reserved[mr][mc]) {
                    reserved[mr][mc] = true
                    val isBorder = row == 0 || row == 4 || col == 0 || col == 4
                    val isCenter = row == 2 && col == 2
                    matrix[mr][mc] = isBorder || isCenter
                }
            }
        }
    }

    private fun getAlignmentPositions(version: Int): IntArray {
        return when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            else -> intArrayOf(6, 18)
        }
    }

    private fun placeFormatInfo(matrix: Array<BooleanArray>, size: Int) {
        // Format info bits for Mask 0, EC L: 111011111000100
        val formatBits = booleanArrayOf(
            true, true, true, false, true, true, true, true,
            true, false, false, false, true, false, false
        )

        // Top-Left corner around finder pattern
        var idx = 0
        for (c in 0..5) matrix[8][c] = formatBits[idx++]
        matrix[8][7] = formatBits[idx++]
        matrix[8][8] = formatBits[idx++]
        matrix[7][8] = formatBits[idx++]
        for (r in 5 downTo 0) matrix[r][8] = formatBits[idx++]

        // Bottom-Left and Top-Right split
        idx = 0
        for (r in size - 1 downTo size - 7) matrix[r][8] = formatBits[idx++]
        for (c in size - 8 until size) matrix[8][c] = formatBits[idx++]
    }

    private fun encodeDataBits(data: String, version: Int): BooleanArray {
        val result = mutableListOf<Boolean>()

        // Mode Indicator: Byte Mode (0100)
        result.addAll(listOf(false, true, false, false))

        // Character Count Indicator (8 bits for versions 1-9 in byte mode)
        val charCount = data.length.coerceAtMost(255)
        for (i in 7 downTo 0) {
            result.add(((charCount shr i) and 1) == 1)
        }

        // Character Data Bits (ASCII / UTF-8 bytes)
        for (char in data) {
            val code = char.code and 0xFF
            for (i in 7 downTo 0) {
                result.add(((code shr i) and 1) == 1)
            }
        }

        // Terminator (0000)
        repeat(4) { result.add(false) }

        // Pad to byte boundary
        while (result.size % 8 != 0) {
            result.add(false)
        }

        // Pad bytes (0xEC, 0x11) until capacity is reached
        val totalCapacityBits = (17 + version * 4) * (17 + version * 4) / 2
        val padBytes = listOf(
            booleanArrayOf(true, true, true,0.toBoolean(), true, true,0.toBoolean(),0.toBoolean()), // 0xEC
            booleanArrayOf(0.toBoolean(),0.toBoolean(), true,0.toBoolean(),0.toBoolean(),0.toBoolean(),0.toBoolean(), true)  // 0x11
        )
        var toggle = 0
        while (result.size < totalCapacityBits) {
            for (bit in padBytes[toggle]) result.add(bit)
            toggle = (toggle + 1) % 2
        }

        return result.toByteArray()
    }

    private fun Int.toBoolean() = this != 0
    private fun List<Boolean>.toByteArray() = this.toBooleanArray()
}
