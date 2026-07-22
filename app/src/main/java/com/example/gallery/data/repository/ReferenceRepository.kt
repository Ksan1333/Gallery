package com.example.gallery.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.local.entity.ReferenceItemEntity
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ReferenceRepository(private val context: Context) {
    private val db = GalleryDatabase.getDatabase(context)
    private val dao = db.referenceDao()

    private val urlClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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
            if (!canLoadReferenceUrl(url)) return@withContext false
            dao.insertItem(
                ReferenceItemEntity(
                    projectId = projectId,
                    localUri = null,
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

    private fun canLoadReferenceUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return false

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        return runCatching {
            urlClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                response.isSuccessful &&
                    !contentType.startsWith("text/html", ignoreCase = true) &&
                    response.body?.byteStream()?.read()?.let { it >= 0 } == true
            }
        }.getOrDefault(false)
    }

    suspend fun finishProject(project: ReferenceProjectEntity) = withContext(Dispatchers.IO) {
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
