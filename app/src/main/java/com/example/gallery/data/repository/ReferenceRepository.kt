package com.example.gallery.data.repository

import android.content.Context
import android.os.Environment
import com.example.gallery.R
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.local.entity.ReferenceItemEntity
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ReferenceRepository(private val context: Context) {
    private val db = GalleryDatabase.getDatabase(context)
    private val dao = db.referenceDao()
    private val client = OkHttpClient()

    fun getAllProjectsFlow(): Flow<List<ReferenceProjectEntity>> = dao.getAllProjectsFlow()

    fun getItemsForProjectFlow(projectId: Long): Flow<List<ReferenceItemEntity>> =
        dao.getItemsForProjectFlow(projectId)

    suspend fun createProject(title: String): Long =
        dao.insertProject(ReferenceProjectEntity(title = title))

    private fun referenceDirFor(projectId: Long): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Gallery/References/$projectId"
        ).absoluteFile
    }

    private fun isManagedReferenceLocalPath(projectId: Long, localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return runCatching {
            val referenceRoot = referenceDirFor(projectId).canonicalFile
            val localFile = File(localPath).canonicalFile
            localFile.path == referenceRoot.path ||
                localFile.path.startsWith(referenceRoot.path + File.separator)
        }.getOrDefault(false)
    }

    suspend fun addReferenceFromUrl(
        projectId: Long,
        url: String,
        title: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val referenceDir = referenceDirFor(projectId)
            if (!referenceDir.exists()) referenceDir.mkdirs()

            val file = File(referenceDir, "ref_${System.currentTimeMillis()}.jpg")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to download image: $response")
                FileOutputStream(file).use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
            }

            dao.insertItem(
                ReferenceItemEntity(
                    projectId = projectId,
                    localUri = file.absolutePath,
                    remoteUrl = url,
                    title = title
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun finishProject(project: ReferenceProjectEntity) = withContext(Dispatchers.IO) {
        val items = dao.getItemsForProject(project.id)
        val screenshotKeyword = context.getString(R.string.book_screenshot_label)
        for (item in items) {
            val localPath = item.localUri
            val isScreenshot = localPath?.contains("screenshot_", ignoreCase = true) == true ||
                item.title.contains(screenshotKeyword, ignoreCase = true) ||
                item.title.contains("Screenshot", ignoreCase = true)
            val shouldDeleteManagedFile =
                !isScreenshot && isManagedReferenceLocalPath(project.id, localPath)

            if (shouldDeleteManagedFile && localPath != null) {
                try {
                    val file = File(localPath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                dao.updateItem(item.copy(localUri = null))
            }
        }
        dao.updateProject(project.copy(status = "FINISHED"))
    }

    suspend fun deleteProject(project: ReferenceProjectEntity) = withContext(Dispatchers.IO) {
        val referenceDir = referenceDirFor(project.id)
        if (referenceDir.exists()) {
            referenceDir.deleteRecursively()
        }
        dao.deleteProject(project)
    }

    suspend fun deleteItem(item: ReferenceItemEntity) = withContext(Dispatchers.IO) {
        item.localUri?.let { localPath ->
            if (isManagedReferenceLocalPath(item.projectId, localPath)) {
                val file = File(localPath)
                if (file.exists()) file.delete()
            }
        }
        dao.deleteItem(item)
    }

    suspend fun downloadItemToLocal(item: ReferenceItemEntity): Boolean = withContext(Dispatchers.IO) {
        if (item.localUri != null) return@withContext true
        try {
            val referenceDir = referenceDirFor(item.projectId)
            if (!referenceDir.exists()) referenceDir.mkdirs()

            val ext = if (item.remoteUrl.contains(".png", ignoreCase = true)) ".png" else ".jpg"
            val file = File(referenceDir, "ref_${System.currentTimeMillis()}$ext")
            val request = Request.Builder().url(item.remoteUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to download: $response")
                FileOutputStream(file).use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
            }

            dao.updateItem(item.copy(localUri = file.absolutePath))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateProject(project: ReferenceProjectEntity) = withContext(Dispatchers.IO) {
        dao.updateProject(project)
    }

    suspend fun addLocalItemForProject(
        projectId: Long,
        localPath: String,
        remoteUrl: String = "",
        title: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.insertItem(
                ReferenceItemEntity(
                    projectId = projectId,
                    localUri = localPath,
                    remoteUrl = remoteUrl,
                    title = title
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
