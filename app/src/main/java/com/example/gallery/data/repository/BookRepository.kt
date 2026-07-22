package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import com.example.gallery.R

data class BookData(
    val id: String,
    val title: String,
    val path: String,
    val type: BookType,
    val pageCount: Int,
    val thumbnailPath: String? = null,
    val folderName: String,
    val folderPath: String
)

enum class BookType { ZIP, PDF }

class BookRepository(private val context: Context) {
    private val thumbDir = File(context.cacheDir, "book_thumbs").apply { if (!exists()) mkdirs() }
    private val indexFile = File(context.cacheDir, "book_index.json")
    private val trashRootsFile = File(context.cacheDir, "book_trash_roots.json")
    private val zipEntryCache = ConcurrentHashMap<String, List<String>>()

    private data class CachedBook(
        val book: BookData,
        val lastModified: Long,
        val fileSize: Long
    )

    suspend fun loadCachedBooks(): List<BookData> = withContext(Dispatchers.IO) {
        loadCachedIndex()
            .filter { File(it.book.path).exists() }
            .map { it.book }
            .sortedBy { it.title }
    }

    suspend fun scanBooks(): List<BookData> = withContext(Dispatchers.IO) {
        val books = mutableListOf<BookData>()
        val cachedBooks = loadCachedIndex().associateBy { it.book.path }
        val refreshedCache = mutableListOf<CachedBook>()
        val root = Environment.getExternalStorageDirectory()

        Log.d(TAG, "Starting storage scan from: ${root.absolutePath}")

        try {
            root.walkTopDown()
                .onEnter { file -> 
                    val name = file.name
                    !name.startsWith(".") && name != "Android"
                }
                .filter { it.isFile }
                .forEach { file ->
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".zip") || nameLower.endsWith(".pdf")) {
                        Log.d(TAG, "Found candidate file: ${file.absolutePath}")
                        val cached = cachedBooks[file.absolutePath]
                        if (
                            cached != null &&
                            cached.lastModified == file.lastModified() &&
                            cached.fileSize == file.length()
                        ) {
                            books.add(cached.book)
                            refreshedCache.add(cached)
                            return@forEach
                        }
                        when {
                            nameLower.endsWith(".zip") -> {
                                try {
                                    ZipFile(file).use { zip ->
                                        val pageCount = zip.entries().asSequence().count { isImageFile(it.name) }
                                        if (pageCount > 0) {
                                            val thumbPath = extractZipThumbnail(file)
                                            val book = BookData(
                                                id = file.absolutePath,
                                                title = file.name,
                                                path = file.absolutePath,
                                                type = BookType.ZIP,
                                                pageCount = pageCount,
                                                thumbnailPath = thumbPath,
                                                folderName = file.parentFile?.name ?: "Unknown",
                                                folderPath = file.parentFile?.absolutePath ?: ""
                                            )
                                            books.add(book)
                                            refreshedCache.add(CachedBook(book, file.lastModified(), file.length()))
                                            Log.d(TAG, "Successfully added ZIP: ${file.name} ($pageCount pages)")
                                        } else {
                                            Log.w(TAG, "Skipping ZIP (no images found): ${file.name}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing ZIP: ${file.name}", e)
                                }
                            }
                            nameLower.endsWith(".pdf") -> {
                                try {
                                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                    val renderer = PdfRenderer(fd)
                                    val pageCount = renderer.pageCount
                                    renderer.close()
                                    fd.close()
                                    val thumbPath = extractPdfThumbnail(file)
                                    val book = BookData(
                                        id = file.absolutePath,
                                        title = file.name,
                                        path = file.absolutePath,
                                        type = BookType.PDF,
                                        pageCount = pageCount,
                                        thumbnailPath = thumbPath,
                                        folderName = file.parentFile?.name ?: "Unknown",
                                        folderPath = file.parentFile?.absolutePath ?: ""
                                    )
                                    books.add(book)
                                    refreshedCache.add(CachedBook(book, file.lastModified(), file.length()))
                                    Log.d(TAG, "Successfully added PDF: ${file.name} ($pageCount pages)")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing PDF: ${file.name}", e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during file walk", e)
        }
        
        Log.d(TAG, "Scan completed. Found ${books.size} books.")
        saveCachedIndex(refreshedCache)
        books.sortedBy { it.title }
    }

    suspend fun moveBookToTrash(book: BookData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(book.path)
            if (!source.exists()) return@withContext false
            val parent = source.parentFile ?: return@withContext false
            val trashDir = File(parent, ".gallery_book_trash").apply { if (!exists()) mkdirs() }
            val target = uniqueTrashFile(trashDir, source.name)
            val moved = source.renameTo(target)
            if (moved) {
                saveCachedIndex(loadCachedIndex().filterNot { it.book.path == book.path })
                saveTrashRoot(parent.absolutePath)
                thumbnailFile(source).takeIf { it.exists() }?.delete()
            }
            moved
        }.onFailure { e ->
            Log.e(TAG, "Failed to move book to trash: ${book.path}", e)
        }.getOrDefault(false)
    }

    suspend fun deleteBookPermanently(book: BookData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(book.path)
            val thumbnail = book.thumbnailPath?.let(::File) ?: thumbnailFile(source)
            val deleted = !source.exists() || source.delete()
            if (deleted) {
                saveCachedIndex(loadCachedIndex().filterNot { it.book.path == book.path })
                thumbnail.takeIf { it.exists() }?.delete()
            }
            deleted
        }.onFailure { e ->
            Log.e(TAG, "Failed to permanently delete book: ${book.path}", e)
        }.getOrDefault(false)
    }

    suspend fun loadTrashedBooks(): List<BookData> = withContext(Dispatchers.IO) {
        val trashedBooks = mutableListOf<BookData>()
        val candidateParents = loadCachedIndex()
            .mapNotNull { File(it.book.path).parentFile }
            .plus(loadTrashRoots().map(::File))
            .distinctBy { it.absolutePath }
        runCatching {
            candidateParents
                .asSequence()
                .map { File(it, ".gallery_book_trash") }
                .filter { it.isDirectory }
                .flatMap { trashDir -> trashDir.listFiles()?.asSequence() ?: emptySequence() }
                .forEach { file ->
                    val nameLower = file.name.lowercase()
                    if (!file.isFile || (!nameLower.endsWith(".zip") && !nameLower.endsWith(".pdf"))) return@forEach
                    val type = if (nameLower.endsWith(".zip")) BookType.ZIP else BookType.PDF
                    val pageCount = if (type == BookType.ZIP) getZipPageCount(file) else getPdfPageCount(file)
                    if (pageCount > 0) {
                        val thumbnail = if (type == BookType.ZIP) extractZipThumbnail(file) else extractPdfThumbnail(file)
                        val originalFolder = file.parentFile?.parentFile
                        trashedBooks.add(
                            BookData(
                                id = "trash:${file.absolutePath.hashCode()}",
                                title = file.name,
                                path = file.absolutePath,
                                type = type,
                                pageCount = pageCount,
                                thumbnailPath = thumbnail,
                                folderName = originalFolder?.name ?: context.getString(R.string.label_book_trash),
                                folderPath = originalFolder?.absolutePath ?: file.parentFile?.absolutePath.orEmpty()
                            )
                        )
                    }
                }
        }.onFailure { e -> Log.w(TAG, "Failed to load trashed books", e) }
        trashedBooks.sortedBy { it.title }
    }

    suspend fun restoreBookFromTrash(book: BookData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(book.path)
            if (!source.exists()) return@withContext false
            val restoreDir = File(book.folderPath).takeIf { it.exists() || it.mkdirs() }
                ?: source.parentFile?.parentFile
                ?: return@withContext false
            val target = uniqueFile(restoreDir, source.name)
            source.renameTo(target)
        }.onFailure { e ->
            Log.e(TAG, "Failed to restore book from trash: ${book.path}", e)
        }.getOrDefault(false)
    }

    suspend fun permanentlyDeleteTrashedBook(book: BookData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(book.path)
            val thumbnail = book.thumbnailPath?.let(::File) ?: thumbnailFile(source)
            val deleted = !source.exists() || source.delete()
            if (deleted) {
                thumbnail.takeIf { it.exists() }?.delete()
            }
            deleted
        }.onFailure { e ->
            Log.e(TAG, "Failed to permanently delete trashed book: ${book.path}", e)
        }.getOrDefault(false)
    }

    private fun loadTrashRoots(): List<String> {
        if (!trashRootsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(trashRootsFile.readText(Charsets.UTF_8))
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun saveTrashRoot(path: String) {
        runCatching {
            val roots = (loadTrashRoots() + path).distinct()
            val array = JSONArray()
            roots.forEach(array::put)
            trashRootsFile.writeText(array.toString(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Failed to save book trash root", it) }
    }

    private fun getZipPageCount(file: File): Int {
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().count { isImageFile(it.name) }
            }
        } catch (_: Exception) { 0 }
    }

    private fun getPdfPageCount(file: File): Int {
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close()
            fd.close()
            count
        } catch (_: Exception) { 0 }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return !name.contains("__MACOSX") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
    }

    private fun extractZipThumbnail(file: File): String? {
        val thumbFile = thumbnailFile(file)
        if (thumbFile.exists()) return thumbFile.absolutePath

        try {
            ZipFile(file).use { zip ->
                val firstImage = zip.entries().asSequence()
                    .filter { isImageFile(it.name) }
                    .sortedBy { it.name }
                    .firstOrNull() ?: return null
                
                val bytes = zip.getInputStream(firstImage).use { it.readBytes() }
                val bitmap = decodeThumbnail(bytes) ?: return null
                FileOutputStream(thumbFile).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output) }
                bitmap.recycle()
                return thumbFile.absolutePath
            }
        } catch (_: Exception) { return null }
    }

    private fun extractPdfThumbnail(file: File): String? {
        val thumbFile = thumbnailFile(file)
        if (thumbFile.exists()) return thumbFile.absolutePath

        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)
            val scale = (THUMBNAIL_LONG_SIDE.toFloat() / maxOf(page.width, page.height)).coerceAtMost(1f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            page.close()
            renderer.close()
            fd.close()
            bitmap.recycle()
            return thumbFile.absolutePath
        } catch (_: Exception) { return null }
    }

    suspend fun getZipPage(filePath: String, pageIndex: Int, maxLongSide: Int = 2400): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val cacheKey = "$filePath:${file.lastModified()}:${file.length()}"
            val entryNames = zipEntryCache.getOrPut(cacheKey) {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .filter { isImageFile(it.name) }
                        .map { it.name }
                        .sorted()
                        .toList()
                }
            }
            ZipFile(file).use { zip ->
                val entry = entryNames.getOrNull(pageIndex)?.let(zip::getEntry) ?: return@withContext null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                decodePage(bytes, maxLongSide)
            }
        } catch (_: Exception) { null }
    }

    private fun thumbnailFile(file: File): File {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}".hashCode().toUInt().toString(16)
        return File(thumbDir, "thumb_$key.jpg")
    }

    private fun uniqueTrashFile(dir: File, name: String): File {
        return uniqueFile(dir, name)
    }

    private fun uniqueFile(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").takeIf { it != name }?.let { ".$it" }.orEmpty()
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_${System.currentTimeMillis()}_$index$ext")
            index++
        }
        return candidate
    }

    private fun decodeThumbnail(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > THUMBNAIL_LONG_SIDE * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: return null
        val scale = (THUMBNAIL_LONG_SIDE.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun decodePage(bytes: ByteArray, maxLongSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxLongSide * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        val scale = (maxLongSide.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun loadCachedIndex(): List<CachedBook> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText(Charsets.UTF_8))
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val type = BookType.valueOf(item.getString("type"))
                val book = BookData(
                    id = item.getString("path"),
                    title = item.getString("title"),
                    path = item.getString("path"),
                    type = type,
                    pageCount = item.getInt("pageCount"),
                    thumbnailPath = item.optString("thumbnailPath").takeIf { it.isNotBlank() },
                    folderName = item.optString("folderName", "Unknown"),
                    folderPath = item.optString("folderPath")
                )
                CachedBook(book, item.getLong("lastModified"), item.getLong("fileSize"))
            }
        }.onFailure { e -> Log.w(TAG, "Failed to read cached book index", e) }.getOrDefault(emptyList())
    }

    private fun saveCachedIndex(books: List<CachedBook>) {
        runCatching {
            val array = JSONArray()
            books.forEach { cached ->
                array.put(
                    JSONObject()
                        .put("title", cached.book.title)
                        .put("path", cached.book.path)
                        .put("type", cached.book.type.name)
                        .put("pageCount", cached.book.pageCount)
                        .put("thumbnailPath", cached.book.thumbnailPath.orEmpty())
                        .put("folderName", cached.book.folderName)
                        .put("folderPath", cached.book.folderPath)
                        .put("lastModified", cached.lastModified)
                        .put("fileSize", cached.fileSize)
                )
            }
            indexFile.writeText(array.toString(), Charsets.UTF_8)
        }.onFailure { e -> Log.w(TAG, "Failed to save cached book index", e) }
    }

    private companion object {
        private const val TAG = "BookRepository"
        const val THUMBNAIL_LONG_SIDE = 480
    }

    suspend fun getPdfPage(filePath: String, pageIndex: Int, maxLongSide: Int = 2400): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(pageIndex)
            val scale = (maxLongSide.toFloat() / maxOf(page.width, page.height)).coerceAtMost(1f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            bitmap
        } catch (_: Exception) { null }
    }
}
