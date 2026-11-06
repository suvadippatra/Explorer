package com.troikoss.continuum_explorer.ui.converter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 100% On-Device Archive & Package Conversion Engine.
 * Supports converting between ZIP (with optional AES-256 encryption via Zip4j),
 * POSIX TAR, GZ, and TAR.GZ (.tgz), as well as bundling multiple files into an archive.
 */
object ArchiveConversionEngine {

    suspend fun convertArchive(
        context: Context,
        sourceFile: File,
        targetExtension: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempExtractDir = File(context.cacheDir, "vert_archive_${System.currentTimeMillis()}")
        try {
            tempExtractDir.mkdirs()
            val srcExt = ConversionFormatMatrix.resolveSourceExtension(sourceFile)
            val dstExt = targetExtension.lowercase().trimStart('.')

            onProgress(0.20f, "Unpacking ${sourceFile.name} (${srcExt.uppercase()})...")
            when (srcExt) {
                "zip", "jar" -> {
                    val zip = if (tuning.zipPassword.isNotBlank()) {
                        ZipFile(sourceFile, tuning.zipPassword.toCharArray())
                    } else {
                        ZipFile(sourceFile)
                    }
                    zip.extractAll(tempExtractDir.absolutePath)
                }
                "tar" -> {
                    FileInputStream(sourceFile).buffered().use { fis ->
                        extractPosixTarStream(fis, tempExtractDir)
                    }
                }
                "tgz" -> {
                    FileInputStream(sourceFile).buffered().use { fis ->
                        GZIPInputStream(fis).use { gzis ->
                            extractPosixTarStream(gzis, tempExtractDir)
                        }
                    }
                }
                "gz" -> {
                    val innerName = sourceFile.nameWithoutExtension.ifBlank { "extracted_payload" }
                    if (innerName.lowercase().endsWith(".tar")) {
                        FileInputStream(sourceFile).buffered().use { fis ->
                            GZIPInputStream(fis).use { gzis ->
                                extractPosixTarStream(gzis, tempExtractDir)
                            }
                        }
                    } else {
                        val outPayload = File(tempExtractDir, innerName)
                        FileInputStream(sourceFile).buffered().use { fis ->
                            GZIPInputStream(fis).use { gzis ->
                                FileOutputStream(outPayload).buffered().use { fos ->
                                    gzis.copyTo(fos)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Single non-archive file being packed into an archive
                    sourceFile.copyTo(File(tempExtractDir, sourceFile.name), overwrite = true)
                }
            }

            onProgress(0.65f, "Repacking into ${dstExt.uppercase()} archive...")
            packDirectoryToArchive(tempExtractDir, dstExt, outputFile, tuning)

            onProgress(1.0f, "Repacked archive to ${outputFile.name}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { tempExtractDir.deleteRecursively() } catch (_: Exception) {}
        }
    }

    /**
     * Bundles an arbitrary list of files into a single ZIP or TAR.GZ archive.
     */
    suspend fun bundleFilesIntoArchive(
        context: Context,
        files: List<File>,
        targetExtension: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "vert_bundle_${System.currentTimeMillis()}")
        try {
            stagingDir.mkdirs()
            files.forEachIndexed { idx, file ->
                if (file.exists()) {
                    onProgress(
                        (idx.toFloat() / files.size.coerceAtLeast(1).toFloat()) * 0.5f,
                        "Staging ${file.name}..."
                    )
                    val target = File(stagingDir, file.name)
                    if (file.isDirectory) {
                        file.copyRecursively(target, overwrite = true)
                    } else {
                        file.copyTo(target, overwrite = true)
                    }
                }
            }
            onProgress(0.60f, "Compressing ${files.size} items into ${outputFile.name}...")
            packDirectoryToArchive(stagingDir, targetExtension.lowercase().trimStart('.'), outputFile, tuning)
            onProgress(1.0f, "Created ${outputFile.name}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { stagingDir.deleteRecursively() } catch (_: Exception) {}
        }
    }

    private fun packDirectoryToArchive(
        sourceDir: File,
        dstExt: String,
        outputFile: File,
        tuning: ConversionTuningConfig
    ) {
        if (outputFile.exists()) outputFile.delete()
        when (dstExt) {
            "zip" -> {
                val hasPassword = tuning.zipPassword.isNotBlank()
                val zipFile = if (hasPassword) {
                    ZipFile(outputFile, tuning.zipPassword.toCharArray())
                } else {
                    ZipFile(outputFile)
                }
                val params = ZipParameters().apply {
                    compressionMethod = if (tuning.zipCompressionLevel <= 0) {
                        CompressionMethod.STORE
                    } else {
                        CompressionMethod.DEFLATE
                    }
                    compressionLevel = when {
                        tuning.zipCompressionLevel <= 2 -> CompressionLevel.FASTEST
                        tuning.zipCompressionLevel <= 4 -> CompressionLevel.FAST
                        tuning.zipCompressionLevel <= 7 -> CompressionLevel.NORMAL
                        else -> CompressionLevel.MAXIMUM
                    }
                    if (hasPassword) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                }
                val children = sourceDir.listFiles().orEmpty()
                for (child in children) {
                    if (child.isDirectory) {
                        zipFile.addFolder(child, params)
                    } else {
                        zipFile.addFile(child, params)
                    }
                }
            }
            "tar" -> {
                FileOutputStream(outputFile).buffered().use { fos ->
                    writePosixTarStream(sourceDir, fos)
                }
            }
            "tgz", "gz" -> {
                FileOutputStream(outputFile).buffered().use { fos ->
                    GZIPOutputStream(fos).use { gzos ->
                        writePosixTarStream(sourceDir, gzos)
                    }
                }
            }
            else -> throw UnsupportedOperationException("Unsupported archive target: .$dstExt")
        }
    }

    /**
     * Writes all files inside `rootDir` into a standard POSIX UStar 512-byte block TAR stream.
     */
    private fun writePosixTarStream(rootDir: File, out: OutputStream) {
        val allFiles = rootDir.walkTopDown().filter { it.isFile }.toList()
        val buffer = ByteArray(8192)
        for (file in allFiles) {
            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath.take(99)
            val size = file.length()
            val mtimeSec = file.lastModified() / 1000L

            val header = ByteArray(512)
            writeAsciiBytes(relPath, header, 0, 100)
            writeOctalBytes(0b110100100L, header, 100, 8) // 0644 file mode
            writeOctalBytes(0L, header, 108, 8)           // uid
            writeOctalBytes(0L, header, 116, 8)           // gid
            writeOctalBytes(size, header, 124, 12)        // file size
            writeOctalBytes(mtimeSec, header, 136, 12)    // mtime
            // Checksum placeholder (8 spaces)
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            header[156] = '0'.code.toByte() // Normal file flag
            writeAsciiBytes("ustar", header, 257, 6)
            writeAsciiBytes("00", header, 263, 2)

            var checksum = 0L
            for (b in header) {
                checksum += (b.toInt() and 0xFF)
            }
            writeOctalBytes(checksum, header, 148, 7)
            header[155] = ' '.code.toByte()

            out.write(header)

            BufferedInputStream(FileInputStream(file)).use { fis ->
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = fis.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }

            val padBytes = ((512 - (size % 512)) % 512).toInt()
            if (padBytes > 0) {
                out.write(ByteArray(padBytes))
            }
        }
        // Two 512-byte zero blocks mark EOF in TAR
        out.write(ByteArray(1024))
        out.flush()
    }

    /**
     * Extracts a POSIX TAR stream into `destDir` with Zip-Slip path traversal protection.
     */
    private fun extractPosixTarStream(input: InputStream, destDir: File) {
        val header = ByteArray(512)
        val canonicalDest = destDir.canonicalPath
        val copyBuf = ByteArray(8192)

        while (true) {
            val bytesRead = readFullyOrEof(input, header)
            if (bytesRead < 512 || header.all { it == 0.toByte() }) break

            val rawName = readNullTerminatedAscii(header, 0, 100)
            val prefix = readNullTerminatedAscii(header, 345, 155)
            val entryName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            if (entryName.isBlank()) continue

            val sizeOctal = readNullTerminatedAscii(header, 124, 12).trim()
            val entrySize = sizeOctal.toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()

            val outFile = File(destDir, entryName)
            if (!outFile.canonicalPath.startsWith(canonicalDest)) {
                skipBytesFully(input, entrySize + ((512 - (entrySize % 512)) % 512))
                continue
            }

            if (typeFlag == '5' || entryName.endsWith("/")) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(outFile)).use { fos ->
                    var remaining = entrySize
                    while (remaining > 0) {
                        val toRead = minOf(copyBuf.size.toLong(), remaining).toInt()
                        val r = input.read(copyBuf, 0, toRead)
                        if (r <= 0) break
                        fos.write(copyBuf, 0, r)
                        remaining -= r
                    }
                }
            }

            val pad = ((512 - (entrySize % 512)) % 512)
            if (pad > 0) {
                skipBytesFully(input, pad)
            }
        }
    }

    private fun readFullyOrEof(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read <= 0) break
            offset += read
        }
        return offset
    }

    private fun skipBytesFully(input: InputStream, count: Long) {
        var remaining = count
        val discard = ByteArray(512)
        while (remaining > 0) {
            val r = input.read(discard, 0, minOf(remaining, 512L).toInt())
            if (r <= 0) break
            remaining -= r
        }
    }

    private fun writeAsciiBytes(str: String, dst: ByteArray, offset: Int, maxLen: Int) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val len = minOf(bytes.size, maxLen - 1)
        System.arraycopy(bytes, 0, dst, offset, len)
        dst[offset + len] = 0
    }

    private fun writeOctalBytes(value: Long, dst: ByteArray, offset: Int, length: Int) {
        val octal = value.toString(8).padStart(length - 1, '0')
        val bytes = octal.toByteArray(Charsets.US_ASCII)
        val copyLen = minOf(bytes.size, length - 1)
        System.arraycopy(bytes, 0, dst, offset, copyLen)
        dst[offset + length - 1] = 0
    }

    private fun readNullTerminatedAscii(src: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = minOf(src.size, offset + length)
        while (end < limit && src[end] != 0.toByte()) {
            end++
        }
        return String(src, offset, end - offset, Charsets.UTF_8)
    }
}
