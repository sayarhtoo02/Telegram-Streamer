package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.EpisodeEntity
import com.example.data.MediaRepository
import com.example.data.QualityEntity
import com.example.data.SeriesEntity
import com.example.data.WatchHistoryEntity
import com.example.parser.CaptionParser
import com.example.tdlib.TdLibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaDao())
    private val tdLibClient = TdLibClient.getInstance(application)

    // Observables from Room
    val allSeries: StateFlow<List<SeriesEntity>> = repository.allSeries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    private val _scrapedChannels = MutableStateFlow<Set<String>>(setOf("tg_stream_test", "myanmar_movie_cloud"))
    val scrapedChannels: StateFlow<Set<String>> = _scrapedChannels.asStateFlow()

    // Observe TDLib Auth State
    val authState: StateFlow<TdApi.AuthorizationState> = tdLibClient.authState

    // Active Playback Context
    private val _activeEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val activeEpisode: StateFlow<EpisodeEntity?> = _activeEpisode.asStateFlow()

    private val _activeQualities = MutableStateFlow<List<QualityEntity>>(emptyList())
    val activeQualities: StateFlow<List<QualityEntity>> = _activeQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow<QualityEntity?>(null)
    val selectedQuality: StateFlow<QualityEntity?> = _selectedQuality.asStateFlow()

    fun selectEpisode(episode: EpisodeEntity) {
        _activeEpisode.value = episode
        viewModelScope.launch {
            val qualities = repository.getQualities(episode.id)
            _activeQualities.value = qualities
            _selectedQuality.value = qualities.firstOrNull()
        }
    }

    fun selectQuality(quality: QualityEntity) {
        _selectedQuality.value = quality
    }

    // --- TDLib Auth Actions ---

    fun initializeTdLibParameters(apiId: Int, apiHash: String) {
        viewModelScope.launch {
            try {
                addLog("Initializing TDLib client params...")
                val params = TdApi.SetTdlibParameters().apply {
                    this.apiId = apiId
                    this.apiHash = apiHash
                    this.systemLanguageCode = "en"
                    this.deviceModel = "Android Phone"
                    this.systemVersion = "Android 13"
                    this.applicationVersion = "1.0.0"
                    this.useMessageDatabase = true
                    this.useChatInfoDatabase = true
                    this.useSavedAnimationsDatabase = false
                    this.useFileDatabase = true
                    this.databaseDirectory = getApplication<Application>().filesDir.absolutePath + "/tdlib_db"
                    this.useSecretChats = false
                }
                tdLibClient.send(params)
                addLog("Parameters accepted by TDLib JNI.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize parameters", e)
                addLog("Error: ${e.message}")
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        viewModelScope.launch {
            try {
                addLog("Sending phone number: $phone")
                tdLibClient.send(TdApi.SetAuthenticationPhoneNumber(phone))
                addLog("Phone number code requested.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed sendPhoneNumber", e)
                addLog("Error: ${e.message}")
            }
        }
    }

    fun sendCode(code: String) {
        viewModelScope.launch {
            try {
                addLog("Verifying code: $code")
                tdLibClient.send(TdApi.CheckAuthenticationCode(code))
                addLog("Verification code accepted.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed sendCode", e)
                addLog("Verification Error: ${e.message}")
            }
        }
    }

    fun sendPassword(password: String) {
        viewModelScope.launch {
            try {
                addLog("Verifying 2FA password...")
                tdLibClient.send(TdApi.CheckAuthenticationPassword(password))
                addLog("2FA Password accepted.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed sendPassword", e)
                addLog("Password Error: ${e.message}")
            }
        }
    }

    // --- Scraper Actions ---

    fun addChannelUsername(username: String) {
        val cleaned = username.trim().removePrefix("@")
        if (cleaned.isNotEmpty()) {
            _scrapedChannels.value = _scrapedChannels.value + cleaned
        }
    }

    fun removeChannelUsername(username: String) {
        _scrapedChannels.value = _scrapedChannels.value - username
    }

    fun triggerMassSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncLogs.value = emptyList()
            addLog("Starting scraper synchronizations...")
            
            for (channel in _scrapedChannels.value) {
                try {
                    syncChannel(channel)
                } catch (e: Exception) {
                    addLog("Sync failed for @$channel: ${e.message}")
                }
            }
            addLog("All tasks completed successfully!")
            _isSyncing.value = false
        }
    }

    private suspend fun syncChannel(channelUsername: String) {
        addLog("Resolving channel @$channelUsername...")
        val chatObj = tdLibClient.send(TdApi.SearchPublicChat(channelUsername)) as? TdApi.Chat
            ?: throw Exception("Could not find public chat for @$channelUsername")

        val chatId = chatObj.id
        addLog("Found channel: '${chatObj.title}' [ID: $chatId]")

        val lastReadMsgId = repository.getChannelSyncMessageId(channelUsername)
        addLog("Reading messages since message id: $lastReadMsgId")

        // Fetch messages history
        val historyResult = tdLibClient.send(
            TdApi.GetChatHistory(chatId, 0L, 0, 50, false)
        ) as? TdApi.Messages ?: throw Exception("Could not retrieve history of @$channelUsername")

        val messages = historyResult.messages ?: emptyArray()
        addLog("Scanning ${messages.size} messages...")

        var parsedCount = 0
        var maxMsgId = lastReadMsgId

        withContext(Dispatchers.IO) {
            for (msg in messages) {
                if (msg.id > maxMsgId) {
                    maxMsgId = msg.id
                }

                val content = msg.content
                if (content is TdApi.MessageVideo) {
                    val videoObj = content.video
                    val captionText = content.caption?.text ?: ""
                    
                    if (captionText.isNotEmpty()) {
                        val parsed = CaptionParser.parseCaption(captionText)
                        if (parsed != null) {
                            parsedCount++
                            repository.saveScrapedContent(
                                seriesTitle = parsed.title,
                                episodeNo = parsed.episode,
                                qualityName = parsed.quality,
                                fileId = videoObj.video.id,
                                messageId = msg.id,
                                chatId = chatId,
                                fileSize = videoObj.video.size,
                                duration = videoObj.duration
                            )
                        }
                    }
                }
            }
        }

        repository.updateChannelSyncMessageId(channelUsername, maxMsgId)
        addLog("Successfully synced channel @$channelUsername! Added $parsedCount video qualities.")
    }

    suspend fun saveWatchHistory(playbackPosition: Long, duration: Long) {
        val episode = _activeEpisode.value ?: return
        val quality = _selectedQuality.value ?: return
        withContext(Dispatchers.IO) {
            repository.insertWatchHistory(
                episodeId = episode.id,
                seriesTitle = episode.seriesTitle,
                episodeNumber = episode.episodeNumber,
                quality = quality.quality,
                playbackPosition = playbackPosition,
                totalDuration = duration
            )
        }
    }

    fun observeEpisodes(seriesTitle: String): Flow<List<EpisodeEntity>> {
        return repository.observeEpisodes(seriesTitle)
    }

    suspend fun getEpisodes(seriesTitle: String): List<EpisodeEntity> {
        return repository.getEpisodes(seriesTitle)
    }

    suspend fun getEpisodeById(id: Long): EpisodeEntity? {
        return repository.getEpisodeById(id)
    }

    suspend fun getQualities(episodeId: Long): List<QualityEntity> {
        return repository.getQualities(episodeId)
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            addLog("Clearing all media database indexes...")
            repository.clearAllData()
            addLog("Database cleaned up.")
        }
    }

    private fun addLog(message: String) {
        _syncLogs.value = _syncLogs.value + "[${System.currentTimeMillis() % 1000000}] $message"
    }
}
