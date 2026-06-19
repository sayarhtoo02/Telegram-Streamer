package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val title: String,
    val coverImageUrl: String = "",
    val rating: String = "N/A"
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["title"],
            childColumns = ["seriesTitle"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesTitle: String,
    val episodeNumber: Int,
    val summary: String = ""
)

@Entity(
    tableName = "qualities",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QualityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    val quality: String, // "1080p", "720p", "480p", "2k", "4k"
    val fileId: Int,
    val messageId: Long,
    val chatId: Long,
    val fileSize: Long,
    val duration: Int
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val episodeId: Long,
    val seriesTitle: String,
    val episodeNumber: Int,
    val quality: String,
    val lastPlaybackPosition: Long,
    val totalDuration: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "channel_syncs")
data class ChannelSyncEntity(
    @PrimaryKey val username: String,
    val lastMessageId: Long
)

// --- DAOs ---

@Dao
interface MediaDao {
    @Query("SELECT * FROM series ORDER BY title ASC")
    fun observeAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE title = :title LIMIT 1")
    suspend fun getSeriesByTitle(title: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: SeriesEntity)

    @Query("SELECT * FROM episodes WHERE seriesTitle = :seriesTitle ORDER BY episodeNumber ASC")
    fun observeEpisodesForSeries(seriesTitle: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesTitle = :seriesTitle ORDER BY episodeNumber ASC")
    suspend fun getEpisodesForSeries(seriesTitle: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seriesTitle = :seriesTitle AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun getEpisode(seriesTitle: String, episodeNumber: Int): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query("SELECT * FROM qualities WHERE episodeId = :episodeId")
    suspend fun getQualitiesForEpisode(episodeId: Long): List<QualityEntity>

    @Query("SELECT * FROM qualities WHERE episodeId = :episodeId AND quality = :quality LIMIT 1")
    suspend fun getQualityForEpisodeAndResol(episodeId: Long, quality: String): QualityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuality(quality: QualityEntity)

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun observeWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getWatchHistoryItem(episodeId: Long): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM channel_syncs WHERE username = :username LIMIT 1")
    suspend fun getChannelSync(username: String): ChannelSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelSync(sync: ChannelSyncEntity)

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisodeById(id: Long): EpisodeEntity?

    // Clean up database content
    @Query("DELETE FROM series")
    suspend fun clearAllData()
}

// --- App Database ---

@Database(
    entities = [
        SeriesEntity::class,
        EpisodeEntity::class,
        QualityEntity::class,
        WatchHistoryEntity::class,
        ChannelSyncEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tg_streamer_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
