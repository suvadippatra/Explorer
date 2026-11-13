package com.troikoss.continuum_explorer.managers

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import java.io.File

object GalleryManager {
    private const val CACHE_TTL_MS = 20_000L

    @Volatile
    private var cachedGalleryFiles: List<UniversalFile>? = null

    @Volatile
    private var cachedGalleryTimestamp: Long = 0L

    @Volatile
    private var cachedGalleryAlbums: List<UniversalFile>? = null

    @Volatile
    private var cachedAlbumsTimestamp: Long = 0L

    private val albumPreviewMap = java.util.concurrent.ConcurrentHashMap<String, UniversalFile>()

    fun getCachedAlbumPreview(dirPath: String): UniversalFile? {
        return albumPreviewMap[dirPath.trimEnd('/')]
    }

    fun invalidateCache() {
        cachedGalleryFiles = null
        cachedGalleryTimestamp = 0L
        cachedGalleryAlbums = null
        cachedAlbumsTimestamp = 0L
        albumPreviewMap.clear()
    }

    // Returns only the media files directly in dirPath (no subdirectories).
    fun getAlbumContents(context: Context, dirPath: String): List<UniversalFile> {
        val normalizedDir = dirPath.trimEnd('/')
        val cachedAll = cachedGalleryFiles
        if (cachedAll != null && (System.currentTimeMillis() - cachedGalleryTimestamp) < CACHE_TTL_MS) {
            val fastFiltered = cachedAll.filter { it.parentId?.trimEnd('/') == normalizedDir }
            if (fastFiltered.isNotEmpty()) return fastFiltered
        }

        val mediaFiles = mutableListOf<UniversalFile>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND " +
            "${MediaStore.Files.FileColumns.DATA} LIKE ? AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "$normalizedDir/%",
            "$normalizedDir/%/%",
            "%/.%"
        )

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val size = c.getLong(sizeIndex)
                    if (size <= 0L) continue
                    val fileName = c.getString(nameIndex) ?: path.substringAfterLast('/')
                    val parentPath = path.substringBeforeLast('/', "").ifEmpty { null }
                    val uFile = UniversalFile(
                        name = fileName,
                        isDirectory = false,
                        lastModified = c.getLong(dateIndex) * 1000L,
                        length = size,
                        provider = LocalProvider,
                        providerId = path,
                        parentId = parentPath,
                    )
                    mediaFiles.add(uFile)
                    if (parentPath != null) {
                        albumPreviewMap.putIfAbsent(parentPath.trimEnd('/'), uFile)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mediaFiles
    }

    /**
     * Zero-disk-syscall MediaStore query using indexed MEDIA_TYPE column + in-memory warm cache.
     * Avoids calling `File(path).exists()` 6,000+ times on the cursor loop, cutting Gallery load time
     * from multiple seconds down to < 20ms.
     */
    fun getGalleryFiles(context: Context, forceRefresh: Boolean = false): List<UniversalFile> {
        val now = System.currentTimeMillis()
        val existing = cachedGalleryFiles
        if (!forceRefresh && existing != null && (now - cachedGalleryTimestamp) < CACHE_TTL_MS) {
            return existing
        }

        val files = ArrayList<UniversalFile>(512)

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND " +
            "${MediaStore.Files.FileColumns.SIZE} > 0 AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "%/Android/%",
            "%/.%"
        )

        try {
            val queryUri = MediaStore.Files.getContentUri("external")

            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val size = c.getLong(sizeIndex)
                    if (size <= 0L) continue
                    val fileName = c.getString(nameIndex) ?: path.substringAfterLast('/')
                    if (fileName.startsWith(".")) continue
                    val parentPath = path.substringBeforeLast('/', "").ifEmpty { null }
                    val uFile = UniversalFile(
                        name = fileName,
                        isDirectory = false,
                        lastModified = c.getLong(dateIndex) * 1000L,
                        length = size,
                        provider = LocalProvider,
                        providerId = path,
                        parentId = parentPath,
                    )
                    files.add(uFile)
                    if (parentPath != null) {
                        albumPreviewMap.putIfAbsent(parentPath.trimEnd('/'), uFile)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedGalleryFiles = files
        cachedGalleryTimestamp = System.currentTimeMillis()
        return files
    }

    fun getGalleryAlbums(context: Context, forceRefresh: Boolean = false): List<UniversalFile> {
        val now = System.currentTimeMillis()
        val existing = cachedGalleryAlbums
        if (!forceRefresh && existing != null && (now - cachedAlbumsTimestamp) < CACHE_TTL_MS) {
            return existing
        }

        // Flat list of all folders containing media, keyed by bucket name -> (dirPath, latestModifiedMs)
        val albums = linkedMapOf<String, Pair<String, Long>>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND " +
            "${MediaStore.Files.FileColumns.SIZE} > 0 AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "%/Android/%",
            "%/.%"
        )

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val bucketIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val bucketName = c.getString(bucketIndex) ?: continue
                    if (bucketName !in albums) {
                        val parentDirPath = path.substringBeforeLast('/', "")
                        if (parentDirPath.isNotEmpty()) {
                            val modMs = c.getLong(dateIndex) * 1000L
                            albums[bucketName] = parentDirPath to modMs
                            val fName = c.getString(nameIndex) ?: path.substringAfterLast('/')
                            albumPreviewMap.putIfAbsent(
                                parentDirPath.trimEnd('/'),
                                UniversalFile(
                                    name = fName,
                                    isDirectory = false,
                                    lastModified = modMs,
                                    length = c.getLong(sizeIndex),
                                    provider = LocalProvider,
                                    providerId = path,
                                    parentId = parentDirPath
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val result = albums.entries.sortedBy { it.key.lowercase() }.map { (name, pair) ->
            val (dirPath, modTime) = pair
            UniversalFile(
                name = name,
                isDirectory = true,
                lastModified = modTime,
                length = 0L,
                provider = LocalProvider,
                providerId = dirPath,
                parentId = dirPath.substringBeforeLast('/', "").ifEmpty { null },
            )
        }
        cachedGalleryAlbums = result
        cachedAlbumsTimestamp = System.currentTimeMillis()
        return result
    }
}