package com.example.gallery.data.local.entity

import androidx.room.ColumnInfo

data class MediaVector(
    val uri: String,
    @ColumnInfo(name = "featureVector") val featureVector: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MediaVector
        if (uri != other.uri) return false
        if (!featureVector.contentEquals(other.featureVector)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + featureVector.contentHashCode()
        return result
    }
}
