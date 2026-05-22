package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

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
    private val TAG = "BookRepository"
    private val thumbDir = File(context.cacheDir, "book_thumbs").apply { if (!exists()) mkdirs() }

    suspend fun scanBooks(): List<BookData> = withContext(Dispatchers.IO) {
        val books = mutableListOf<BookData>()
        val root = Environment.getExternalStorageDirectory()
        
        Log.d(TAG, "Starting storage scan from: ${root.absolutePath}")
        
        try {
            root.walkTopDown()
                .onEnter { file -> 
                    val name = file.name
                    val shouldEnter = !name.startsWith(".") && name != "Android"
                    if (shouldEnter) {
                        Log.v(TAG, "Entering directory: ${file.absolutePath}")
                    }
                    shouldEnter
                }
                .filter { it.isFile }
                .forEach { file ->
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".zip") || nameLower.endsWith(".pdf")) {
                        Log.d(TAG, "Found candidate file: ${file.absolutePath}")
                        when {
                            nameLower.endsWith(".zip") -> {
                                try {
                                    ZipFile(file).use { zip ->
                                        val pageCount = zip.entries().asSequence().count { isImageFile(it.name) }
                                        if (pageCount > 0) {
                                            val thumbPath = extractZipThumbnail(file)
                                            books.add(BookData(
                                                id = file.absolutePath,
                                                title = file.name,
                                                path = file.absolutePath,
                                                type = BookType.ZIP,
                                                pageCount = pageCount,
                                                thumbnailPath = thumbPath,
                                                folderName = file.parentFile?.name ?: "Unknown",
                                                folderPath = file.parentFile?.absolutePath ?: ""
                                            ))
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
                                    books.add(BookData(
                                        id = file.absolutePath,
                                        title = file.name,
                                        path = file.absolutePath,
                                        type = BookType.PDF,
                                        pageCount = pageCount,
                                        thumbnailPath = thumbPath,
                                        folderName = file.parentFile?.name ?: "Unknown",
                                        folderPath = file.parentFile?.absolutePath ?: ""
                                    ))
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
        books.sortedBy { it.title }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return !name.contains("__MACOSX") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
    }

    private fun extractZipThumbnail(file: File): String? {
        val thumbFile = File(thumbDir, "thumb_${file.name}.jpg")
        if (thumbFile.exists()) return thumbFile.absolutePath

        try {
            ZipFile(file).use { zip ->
                val firstImage = zip.entries().asSequence()
                    .filter { isImageFile(it.name) }
                    .sortedBy { it.name }
                    .firstOrNull() ?: return null
                
                zip.getInputStream(firstImage).use { input ->
                    FileOutputStream(thumbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return thumbFile.absolutePath
            }
        } catch (e: Exception) { return null }
    }

    private fun extractPdfThumbnail(file: File): String? {
        val thumbFile = File(thumbDir, "thumb_${file.name}.jpg")
        if (thumbFile.exists()) return thumbFile.absolutePath

        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            page.close()
            renderer.close()
            fd.close()
            return thumbFile.absolutePath
        } catch (e: Exception) { return null }
    }

    suspend fun getZipPage(filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ZipFile(File(filePath)).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { isImageFile(it.name) }
                    .sortedBy { it.name }
                    .toList()
                
                val entry = entries.getOrNull(pageIndex) ?: return@withContext null
                zip.getInputStream(entry).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            }
        } catch (e: Exception) { null }
    }

    suspend fun getPdfPage(filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            bitmap
        } catch (e: Exception) { null }
    }
}
