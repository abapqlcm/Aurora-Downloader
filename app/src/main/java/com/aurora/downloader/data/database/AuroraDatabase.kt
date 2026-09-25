package com.aurora.downloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AuroraDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun partDao(): PartDao

    companion object {
        @Volatile
        private var instance: AuroraDatabase? = null

        fun get(context: Context): AuroraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuroraDatabase::class.java,
                    "aurora.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
