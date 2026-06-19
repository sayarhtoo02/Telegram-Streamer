package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.EpisodeEntity
import com.example.data.QualityEntity
import com.example.data.SeriesEntity
import com.example.service.TelegramSyncService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StreamSelectionScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

sealed class AppScreen {
    object AUTH : AppScreen()
    object HOME : AppScreen()
    data class DETAIL(val series: SeriesEntity) : AppScreen()
    data class STREAM_SELECT(val episode: EpisodeEntity) : AppScreen()
    data class PLAYER(val episode: EpisodeEntity, val quality: QualityEntity) : AppScreen()
    object SETTINGS : AppScreen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start the long-lived JNI sync service
        TelegramSyncService.startSyncService(this)

        setContent {
            MyApplicationTheme {
                val coroutineScope = rememberCoroutineScope()
                val authState by viewModel.authState.collectAsState()
                
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.AUTH) }
                val backStack = remember { mutableListOf<AppScreen>() }

                fun navigateTo(screen: AppScreen) {
                    backStack.add(currentScreen)
                    currentScreen = screen
                }

                fun navigateBack() {
                    if (backStack.isNotEmpty()) {
                        currentScreen = backStack.removeAt(backStack.size - 1)
                    } else {
                        currentScreen = AppScreen.HOME
                    }
                }

                // Auto-routing based on authentication updates from MTProto session
                if (authState is TdApi.AuthorizationStateReady && currentScreen == AppScreen.AUTH) {
                    currentScreen = AppScreen.HOME
                } else if (authState !is TdApi.AuthorizationStateReady && currentScreen != AppScreen.AUTH) {
                    currentScreen = AppScreen.AUTH
                    backStack.clear()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val screen = currentScreen) {
                        is AppScreen.AUTH -> {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    currentScreen = AppScreen.HOME
                                }
                            )
                        }

                        is AppScreen.HOME -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onSeriesSelected = { series -> navigateTo(AppScreen.DETAIL(series)) },
                                onContinueWatchingSelected = { historyItem ->
                                    coroutineScope.launch {
                                        val ep = viewModel.getEpisodeById(historyItem.episodeId)
                                        val qualityList = viewModel.getQualities(historyItem.episodeId)
                                        val qual = qualityList.find { it.quality == historyItem.quality } ?: qualityList.firstOrNull()
                                        if (ep != null && qual != null) {
                                            viewModel.selectEpisode(ep)
                                            viewModel.selectQuality(qual)
                                            navigateTo(AppScreen.PLAYER(ep, qual))
                                        }
                                    }
                                },
                                onNavigateToSettings = { navigateTo(AppScreen.SETTINGS) }
                            )
                        }

                        is AppScreen.DETAIL -> {
                            DetailScreen(
                                viewModel = viewModel,
                                series = screen.series,
                                onEpisodeSelected = { episode ->
                                    viewModel.selectEpisode(episode)
                                    navigateTo(AppScreen.STREAM_SELECT(episode))
                                },
                                onBack = { navigateBack() }
                            )
                        }

                        is AppScreen.STREAM_SELECT -> {
                            StreamSelectionScreen(
                                viewModel = viewModel,
                                episode = screen.episode,
                                onQualitySelected = { quality ->
                                    navigateTo(AppScreen.PLAYER(screen.episode, quality))
                                },
                                onBack = { navigateBack() }
                            )
                        }

                        is AppScreen.PLAYER -> {
                            PlayerScreen(
                                viewModel = viewModel,
                                episode = screen.episode,
                                quality = screen.quality,
                                onNavigateToNextEpisode = { nextEp, nextQual ->
                                    currentScreen = AppScreen.PLAYER(nextEp, nextQual)
                                },
                                onClosePlayer = { navigateBack() }
                            )
                        }

                        is AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // We do not stop the foreground service so the active download stream continues
        super.onDestroy()
    }
}
