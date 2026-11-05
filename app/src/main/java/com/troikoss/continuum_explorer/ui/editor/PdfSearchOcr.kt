package com.troikoss.continuum_explorer.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result representing a search match on a PDF page with text snippet
 * and visual normalized bounding box coordinates (0f..1f) for highlighting.
 */
data class PdfSearchResult(
    val pageIndex: Int,
    val matchedTextSnippet: String,
    val normalizedHighlights: List<RectF> = emptyList()
)

object PdfSearchOcr {

    /**
     * Search for a query inside a PDF file.
     * 1. Uses PDFBox embedded text extraction for ultra-fast document search.
     * 2. Extracts bounding box coordinates via ML Kit Text Recognition on matched pages.
     * 3. If no embedded text exists (e.g. scanned image document), falls back to full ML Kit OCR.
     */
    suspend fun searchPdf(
        context: Context,
        file: UniversalFile,
        query: String,
        totalPages: Int,
        onProgress: (searchedPage: Int, total: Int) -> Unit = { _, _ -> }
    ): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<PdfSearchResult>()
        val lowerQuery = query.trim().lowercase()
        val matchedPageIndices = mutableListOf<Pair<Int, String>>()

        // Phase 1: PDFBox Text Stripper (Fast embedded text search)
        try {
            PDFBoxResourceLoader.init(context)
            val fileRef = file.fileRef
            val resolvedUri = file.documentFileRef?.uri ?: getUriForUniversalFile(context, file)

            val doc = if (fileRef != null && fileRef.exists()) {
                PDDocument.load(fileRef)
            } else if (resolvedUri != null) {
                context.contentResolver.openInputStream(resolvedUri)?.use { PDDocument.load(it) }
            } else null

            if (doc != null) {
                val stripper = PDFTextStripper()
                val pageCount = doc.numberOfPages
                for (p in 0 until pageCount) {
                    onProgress(p + 1, pageCount)
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    val text = stripper.getText(doc)
                    if (text.contains(lowerQuery, ignoreCase = true)) {
                        val snippet = extractSnippet(text, lowerQuery)
                        matchedPageIndices.add(p to snippet)
                    }
                }
                doc.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Phase 2: If matches were found via embedded text, get coordinates using ML Kit on those matched pages
        if (matchedPageIndices.isNotEmpty()) {
            val pfd = getParcelFileDescriptor(context, file)
            if (pfd != null) {
                try {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    PdfRenderer(pfd).use { renderer ->
                        for ((pageIdx, snippet) in matchedPageIndices) {
                            if (pageIdx < renderer.pageCount) {
                                val highlights = extractCoordinatesForPage(renderer, pageIdx, lowerQuery, recognizer)
                                results.add(PdfSearchResult(pageIdx, snippet, highlights))
                            } else {
                                results.add(PdfSearchResult(pageIdx, snippet, emptyList()))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    for ((pageIdx, snippet) in matchedPageIndices) {
                        results.add(PdfSearchResult(pageIdx, snippet, emptyList()))
                    }
                } finally {
                    try { pfd.close() } catch (_: Exception) {}
                }
            } else {
                for ((pageIdx, snippet) in matchedPageIndices) {
                    results.add(PdfSearchResult(pageIdx, snippet, emptyList()))
                }
            }
            return@withContext results
        }

        // Phase 3: Scanned PDF fallback - Full ML Kit OCR scanning across rendered pages
        if (results.isEmpty() && totalPages > 0) {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val pfd = getParcelFileDescriptor(context, file)
                if (pfd != null) {
                    PdfRenderer(pfd).use { renderer ->
                        val count = minOf(totalPages, renderer.pageCount)
                        for (p in 0 until count) {
                            onProgress(p + 1, count)
                            val page = renderer.openPage(p)
                            val targetW = (page.width * 0.8f).toInt().coerceAtLeast(200)
                            val targetH = (page.height * 0.8f).toInt().coerceAtLeast(200)
                            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()

                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            val visionText = Tasks.await(recognizer.process(inputImage))

                            if (visionText.text.contains(lowerQuery, ignoreCase = true)) {
                                val snippet = extractSnippet(visionText.text, lowerQuery)
                                val highlights = mutableListOf<RectF>()

                                for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        if (line.text.contains(lowerQuery, ignoreCase = true)) {
                                            for (element in line.elements) {
                                                if (element.text.contains(lowerQuery, ignoreCase = true)) {
                                                    val box = element.boundingBox ?: line.boundingBox
                                                    if (box != null) {
                                                        highlights.add(
                                                            RectF(
                                                                box.left.toFloat() / targetW,
                                                                box.top.toFloat() / targetH,
                                                                box.right.toFloat() / targetW,
                                                                box.bottom.toFloat() / targetH
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                results.add(PdfSearchResult(p, snippet, highlights))
                            }
                            bitmap.recycle()
                        }
                    }
                    pfd.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext results
    }

    private fun extractCoordinatesForPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        lowerQuery: String,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): List<RectF> {
        return try {
            val page = renderer.openPage(pageIndex)
            val targetW = (page.width * 0.8f).toInt().coerceAtLeast(200)
            val targetH = (page.height * 0.8f).toInt().coerceAtLeast(200)
            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage))
            val highlights = mutableListOf<RectF>()

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    if (line.text.contains(lowerQuery, ignoreCase = true)) {
                        for (element in line.elements) {
                            if (element.text.contains(lowerQuery, ignoreCase = true)) {
                                val box = element.boundingBox ?: line.boundingBox
                                if (box != null) {
                                    highlights.add(
                                        RectF(
                                            box.left.toFloat() / targetW,
                                            box.top.toFloat() / targetH,
                                            box.right.toFloat() / targetW,
                                            box.bottom.toFloat() / targetH
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            bitmap.recycle()
            highlights
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractSnippet(fullText: String, lowerQuery: String): String {
        val index = fullText.lowercase().indexOf(lowerQuery)
        if (index < 0) return fullText.take(60)
        val start = (index - 20).coerceAtLeast(0)
        val end = (index + lowerQuery.length + 20).coerceAtMost(fullText.length)
        return "..." + fullText.substring(start, end).replace("\n", " ").trim() + "..."
    }

    private fun getParcelFileDescriptor(context: Context, file: UniversalFile): ParcelFileDescriptor? {
        val fileRef = file.fileRef
        if (fileRef != null && fileRef.exists()) {
            return ParcelFileDescriptor.open(fileRef, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val uri = file.documentFileRef?.uri ?: getUriForUniversalFile(context, file)
        if (uri != null) {
            return context.contentResolver.openFileDescriptor(uri, "r")
        }
        return null
    }
}
