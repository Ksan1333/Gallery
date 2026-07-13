package com.example.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.data.local.migrations.Migration15To16
import com.example.gallery.data.local.migrations.Migration16To17
import com.example.gallery.data.local.migrations.Migration18To19

@Database(
    entities = [
        MediaMetadataEntity::class,
        TagEntity::class,
        com.example.gallery.data.local.entity.FolderOrderEntity::class,
        com.example.gallery.data.local.entity.ManagedFolderEntity::class,
        com.example.gallery.data.local.entity.TagTranslationEntity::class,
        com.example.gallery.data.local.entity.VideoDownloadEntity::class,
        com.example.gallery.data.local.entity.MeasureStatsEntity::class,
        com.example.gallery.data.local.entity.ReferenceProjectEntity::class,
        com.example.gallery.data.local.entity.ReferenceItemEntity::class
    ],
    version = 19,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun referenceDao(): com.example.gallery.data.local.dao.ReferenceDao

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
                    .addMigrations(Migration15To16, Migration16To17, Migration18To19)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
