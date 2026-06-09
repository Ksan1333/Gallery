package com.example.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity

@Database(
    entities = [
        MediaMetadataEntity::class,
        TagEntity::class,
        com.example.gallery.data.local.entity.FolderOrderEntity::class,
        com.example.gallery.data.local.entity.ManagedFolderEntity::class,
        com.example.gallery.data.local.entity.TagTranslationEntity::class,
        com.example.gallery.data.local.entity.VideoDownloadEntity::class,
        com.example.gallery.data.local.entity.MeasureStatsEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getDatabase(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "gallery_database",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
