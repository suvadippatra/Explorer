package com.troikoss.continuum_explorer.ui.converter

import android.content.Context
import android.os.Environment
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import java.io.File

/**
 * Central dispatcher that routes each `ConverterQueueItem` to its specialized native engine
 * (Image, Document/Data, Audio/Video, or Archive), resolves non-colliding output file paths,
 * and records every converted output into `SuiteRecentDatabase` so it immediately appears
 * in My Suite -> Recent Edited & Created Files.
 */
object FileConverterDispatcher {

    fun createQueueItem(file: File): ConverterQueueItem? {
        if (!file.exists() || file.isDirectory) return null
        val srcExt = ConversionFormatMatrix.resolveSourceExtension(file)
        val targets = ConversionFormatMatrix.getAvailableTargets(srcExt)
        if (targets.isEmpty()) return null
        val category = ConversionFormatMatrix.resolveCategory(srcExt)
        val defaultTarget = ConversionFormatMatrix.getDefaultTarget(srcExt)
        return ConverterQueueItem(
            sourceFile = file,
            sourceExtension = srcExt,
            category = category,
            availableTargets = targets,
            targetFormat = defaultTarget
        )
    }

    fun resolveOutputDirectory(
        sourceFile: File,
        destinationMode: OutputDestinationMode,
        customFolder: File?
    ): File {
        val fallbackSuiteDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ContinuumSuite/Converted"
        ).apply { if (!exists()) mkdirs() }

        val baseParent = sourceFile.parentFile?.takeIf { it.exists() && it.canWrite() }
            ?: fallbackSuiteDir
        val dir = when (destinationMode) {
            OutputDestinationMode.CONVERTED_SUBFOLDER -> File(baseParent, "Converted")
            OutputDestinationMode.SAME_AS_SOURCE -> baseParent
            OutputDestinationMode.CUSTOM_FOLDER -> customFolder?.takeIf { it.exists() || it.mkdirs() } ?: fallbackSuiteDir
        }
        if (!dir.exists()) dir.mkdirs()
        return if (dir.exists() && dir.canWrite()) dir else fallbackSuiteDir
    }

    fun resolveUniqueOutputFile(
        sourceFile: File,
        targetFormat: String,
        destinationMode: OutputDestinationMode,
        customFolder: File?
    ): File {
        val outDir = resolveOutputDirectory(sourceFile, destinationMode, customFolder)
        val rawStem = if (sourceFile.name.lowercase().endsWith(".tar.gz")) {
            sourceFile.name.dropLast(7)
        } else {
            sourceFile.nameWithoutExtension.ifBlank { "converted" }
        }
        val cleanExt = targetFormat.lowercase().trimStart('.')
        var candidate = File(outDir, "$rawStem.$cleanExt")
        if (candidate.absolutePath == sourceFile.absolutePath || candidate.exists()) {
            var counter = 1
            while (candidate.absolutePath == sourceFile.absolutePath || candidate.exists()) {
                candidate = File(outDir, "${rawStem}_converted_$counter.$cleanExt")
                counter++
            }
        }
        return candidate
    }

    suspend fun convertSingleItem(
        context: Context,
        item: ConverterQueueItem,
        destinationMode: OutputDestinationMode,
        customFolder: File?,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val outputFile = resolveUniqueOutputFile(
            sourceFile = item.sourceFile,
            targetFormat = item.targetFormat,
            destinationMode = destinationMode,
            customFolder = customFolder
        )

        val result = when (item.category) {
            ConversionCategory.IMAGE -> ImageConversionEngine.convertImage(
                context = context,
                sourceFile = item.sourceFile,
                targetExtension = item.targetFormat,
                outputFile = outputFile,
                tuning = item.tuning,
                onProgress = onProgress
            )
            ConversionCategory.DOCUMENT_DATA -> DocumentDataConversionEngine.convertDocumentOrData(
                context = context,
                sourceFile = item.sourceFile,
                targetExtension = item.targetFormat,
                outputFile = outputFile,
                tuning = item.tuning,
                onProgress = onProgress
            )
            ConversionCategory.AUDIO_VIDEO -> MediaConversionEngine.convertMedia(
                sourceFile = item.sourceFile,
                targetExtension = item.targetFormat,
                outputFile = outputFile,
                tuning = item.tuning,
                onProgress = onProgress
            )
            ConversionCategory.ARCHIVE -> ArchiveConversionEngine.convertArchive(
                context = context,
                sourceFile = item.sourceFile,
                targetExtension = item.targetFormat,
                outputFile = outputFile,
                tuning = item.tuning,
                onProgress = onProgress
            )
            ConversionCategory.ALL -> DocumentDataConversionEngine.convertDocumentOrData(
                context = context,
                sourceFile = item.sourceFile,
                targetExtension = item.targetFormat,
                outputFile = outputFile,
                tuning = item.tuning,
                onProgress = onProgress
            )
        }

        result.onSuccess { generatedFile ->
            if (generatedFile.exists() && generatedFile.isFile) {
                SuiteRecentDatabase.recordFileActivity(
                    context = context,
                    file = generatedFile,
                    editorToolId = "file_converter",
                    editorToolName = "File Converter (VERT)",
                    activityType = SuiteActivityType.CREATED
                )
            }
        }

        return result
    }
}
