package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val uri: String,
    val dateAdded: Long = 0,
    val mimeType: String? = null,
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0,
    val fileName: String = "",
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val ageRating: String = "SFW", // "SFW", "R15", "R18"
    val isAiAnalyzed: Boolean = false,
    val featureVector: FloatArray? = null, // TensorFlow Lite embedding
    val isDeleted: Boolean = false,
    val deletedDate: Long? = null,
    val hasThumbnail: Boolean = false  // サムネイル生成済みフラグ
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MediaMetadataEntity
        if (uri != other.uri) return false
        if (dateAdded != other.dateAdded) return false
        if (mimeType != other.mimeType) return false
        if (duration != other.duration) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fileSize != other.fileSize) return false
        if (fileName != other.fileName) return false
        if (isFavorite != other.isFavorite) return false
        if (ageRating != other.ageRating) return false
        if (isAiAnalyzed != other.isAiAnalyzed) return false
        if (featureVector != null) {
            if (other.featureVector == null) return false
            if (!featureVector.contentEquals(other.featureVector)) return false
        } else if (other.featureVector != null) return false
        if (folderName != other.folderName) return false
        if (isDeleted != other.isDeleted) return false
        if (deletedDate != other.deletedDate) return false
        if (hasThumbnail != other.hasThumbnail) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + ageRating.hashCode()
        result = 31 * result + isAiAnalyzed.hashCode()
        result = 31 * result + (featureVector?.contentHashCode() ?: 0)
        result = 31 * result + folderName.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + (deletedDate?.hashCode() ?: 0)
        result = 31 * result + hasThumbnail.hashCode()
        return result
    }
}

data class MediaMetadataSummary(
    val uri: String,
    val dateAdded: Long,
    val mimeType: String?,
    val duration: Long,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val fileName: String,
    val isFavorite: Boolean,
    val ageRating: String,
    val isAiAnalyzed: Boolean,
    val folderName: String,
    val isDeleted: Boolean,
    val deletedDate: Long?,
    val hasFeatureVector: Boolean,
    val hasThumbnail: Boolean = false
)
