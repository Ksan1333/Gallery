package com.example.gallery.util

import android.graphics.Bitmap
import android.util.Log
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object BookPageCacheManager {
    private const val TAG = "BookPageCache"
    private val cachedPages = ConcurrentHashMap<Int, Bitmap>()
    private var currentBookId: String? = null
    private val preparationId = AtomicInteger(0)

    suspend fun prepareCache(book: BookData, repository: BookRepository) {
        if (currentBookId == book.id && cachedPages.isNotEmpty()) return

        clearCache()
        val myId = preparationId.get()
        currentBookId = book.id

        withContext(Dispatchers.IO) {
            try {
                for (i in 0 until book.pageCount) {
                    if (myId != preparationId.get()) return@withContext
                    
                    val bitmap = if (book.type == BookType.ZIP) {
                        repository.getZipPage(book.path, i, 480)
                    } else {
                        repository.getPdfPage(book.path, i, 480)
                    }
                    if (bitmap != null) {
                        cachedPages[i] = bitmap
                    }
                }
                Log.d(TAG, "Cache prepared: ${cachedPages.size} pages for ${book.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare book cache", e)
            }
        }
    }

    fun getPage(index: Int): Bitmap? = cachedPages[index]

    fun clearCache() {
        preparationId.incrementAndGet()
        cachedPages.values.forEach { it.recycle() }
        cachedPages.clear()
        currentBookId = null
    }
}
