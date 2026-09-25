package com.aurora.downloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.domain.model.PartEntity

class Converters {

    @TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToStatus(name: String): DownloadStatus = DownloadStatus.valueOf(name)
}

@Database(
    entities = [DownloadEntity::class, PartEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuroraDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun partDao(): PartDao

    companion object {
        @Volatile
        private var instance: AuroraDatabase? = null

        /** v2 adds target_directory + content_uri + published columns to track
         *  the staging path and files moved into the public Downloads/RDM. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN target_directory TEXT")
                database.execSQL("ALTER TABLE downloads ADD COLUMN content_uri TEXT")
                database.execSQL("ALTER TABLE downloads ADD COLUMN published INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AuroraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuroraDatabase::class.java,
                    "aurora.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
