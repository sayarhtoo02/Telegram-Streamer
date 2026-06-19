package com.example.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {

    val allSeries: Flow<List<SeriesEntity>> = mediaDao.observeAllSeries()
    val watchHistory: Flow<List<WatchHistoryEntity>> = mediaDao.observeWatchHistory()

    fun observeEpisodes(seriesTitle: String): Flow<List<EpisodeEntity>> {
        return mediaDao.observeEpisodesForSeries(seriesTitle)
    }

    suspend fun getEpisodes(seriesTitle: String): List<EpisodeEntity> {
        return mediaDao.getEpisodesForSeries(seriesTitle)
    }

    suspend fun getEpisodeById(id: Long): EpisodeEntity? {
        return mediaDao.getEpisodeById(id)
    }

    suspend fun getQualities(episodeId: Long): List<QualityEntity> {
        return mediaDao.getQualitiesForEpisode(episodeId)
    }

    suspend fun getSeries(title: String): SeriesEntity? {
        return mediaDao.getSeriesByTitle(title)
    }

    suspend fun insertWatchHistory(
        episodeId: Long,
        seriesTitle: String,
        episodeNumber: Int,
        quality: String,
        playbackPosition: Long,
        totalDuration: Long
    ) {
        val history = WatchHistoryEntity(
            episodeId = episodeId,
            seriesTitle = seriesTitle,
            episodeNumber = episodeNumber,
            quality = quality,
            lastPlaybackPosition = playbackPosition,
            totalDuration = totalDuration,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
        mediaDao.insertWatchHistory(history)
    }

    suspend fun saveScrapedContent(
        seriesTitle: String,
        episodeNo: Int,
        qualityName: String,
        fileId: Int,
        messageId: Long,
        chatId: Long,
        fileSize: Long,
        duration: Int
    ) {
        // Ensure Series entity exists
        var series = mediaDao.getSeriesByTitle(seriesTitle)
        if (series == null) {
            series = SeriesEntity(
                title = seriesTitle,
                coverImageUrl = "", // will be generated dynamically or default-assigned
                rating = "7.8" // standard rating placeholder
            )
            mediaDao.insertSeries(series)
        }

        // Ensure Episode entity exists
        var episode = mediaDao.getEpisode(seriesTitle, episodeNo)
        val episodeId = if (episode == null) {
            val newEpisode = EpisodeEntity(
                seriesTitle = seriesTitle,
                episodeNumber = episodeNo,
                summary = "Episode $episodeNo of the $seriesTitle series retrieved directly from the scraping index."
            )
            mediaDao.insertEpisode(newEpisode)
        } else {
            episode.id
        }

        // Ensure Quality entity exists
        val existingQual = mediaDao.getQualityForEpisodeAndResol(episodeId, qualityName)
        if (existingQual == null) {
            val qualityObj = QualityEntity(
                episodeId = episodeId,
                quality = qualityName,
                fileId = fileId,
                messageId = messageId,
                chatId = chatId,
                fileSize = fileSize,
                duration = duration
            )
            mediaDao.insertQuality(qualityObj)
        }
    }

    suspend fun getChannelSyncMessageId(username: String): Long {
        return mediaDao.getChannelSync(username)?.lastMessageId ?: 0L
    }

    suspend fun updateChannelSyncMessageId(username: String, lastMessageId: Long) {
        mediaDao.insertChannelSync(ChannelSyncEntity(username, lastMessageId))
    }

    suspend fun clearAllData() {
        mediaDao.clearAllData()
    }
}
