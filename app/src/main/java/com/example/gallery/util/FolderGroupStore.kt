package com.example.gallery.util

import android.content.SharedPreferences
import com.example.gallery.data.local.PreferenceManager
import java.net.URLDecoder
import java.net.URLEncoder

data class FolderGroupDefinition(
    val id: String,
    val title: String,
    val folderIds: List<String>
)

object FolderGroupCodec {
    private const val VERSION = "v1"

    fun encode(groups: List<FolderGroupDefinition>): String = buildString {
        appendLine(VERSION)
        groups.forEach { group ->
            val fields = listOf(group.id, group.title) + group.folderIds.distinct()
            appendLine(fields.joinToString("\t") { encodeField(it) })
        }
    }.trimEnd()

    fun decode(raw: String?): List<FolderGroupDefinition> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val fields = line.split('\t').mapNotNull(::decodeField)
            val folderIds = fields.drop(2).filter { it.isNotBlank() }.distinct()
            if (fields.size < 4 || fields[0].isBlank() || folderIds.size < 2) {
                null
            } else {
                FolderGroupDefinition(fields[0], fields[1].ifBlank { fields[0] }, folderIds)
            }
        }.distinctBy { it.id }
    }

    private fun encodeField(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decodeField(value: String): String? = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrNull()
}

object FolderGroupStore {
    fun load(prefs: SharedPreferences): List<FolderGroupDefinition> =
        FolderGroupCodec.decode(prefs.getString(PreferenceManager.FOLDER_GROUPS_DATA, null))

    fun save(prefs: SharedPreferences, groups: List<FolderGroupDefinition>) {
        prefs.edit()
            .putString(PreferenceManager.FOLDER_GROUPS_DATA, FolderGroupCodec.encode(groups))
            .apply()
    }
}
