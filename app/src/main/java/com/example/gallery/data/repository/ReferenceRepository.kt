package com.example.gallery.data.repository

import android.content.Context
import android.os.Environment
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.local.entity.ReferenceItemEntity
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ReferenceRepository(private val context: Context) {
    private val db = GalleryDatabase.getDatabase(context)
    private val dao = db.referenceDao()

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
