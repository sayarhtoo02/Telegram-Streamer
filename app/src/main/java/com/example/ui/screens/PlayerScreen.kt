package com.example.ui.screens

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.EpisodeEntity
import com.example.data.QualityEntity
import com.example.media.TdLibDataSourceFactory
import com.example.tdlib.TdLibClient
import com.example.ui.theme.CardBackground
import com.example.ui.theme.NetflixRed
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    episode: EpisodeEntity,
    quality: QualityEntity,
    onNavigateToNextEpisode: (EpisodeEntity, QualityEntity) -> Unit,
    onClosePlayer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tdLibClient = TdLibClient.getInstance(context)

    var hasPlaybackFinished by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    
    // Instantiate ExoPlayer keyed by fileId to re-initialize when quality/episode changes
    val exoPlayer = remember(quality.fileId) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse("tdlib://stream?file_id=${quality.fileId}"))
                .build()
            val customDataSourceFactory = TdLibDataSourceFactory(tdLibClient)
            val mediaSource = ProgressiveMediaSource.Factory(customDataSourceFactory)
                .createMediaSource(mediaItem)
                
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    // Capture standard system Back buttons and pause/close player
    BackHandler {
        coroutineScope.launch {
            viewModel.saveWatchHistory(currentPositionMs, totalDurationMs)
            onClosePlayer()
        }
    }

    // Playback state tracker with automatic lifecycle cleanup/release
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    hasPlaybackFinished = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Progress updates to Room WatchHistory periodic loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1000)
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                viewModel.saveWatchHistory(currentPositionMs, totalDurationMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Player UI
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close controller button overlay
        IconButton(
            onClick = {
                coroutineScope.launch {
                    viewModel.saveWatchHistory(currentPositionMs, totalDurationMs)
                    onClosePlayer()
                }
            },
            modifier = Modifier
                .padding(top = 40.dp, end = 16.dp)
                .align(Alignment.TopEnd)
                .testTag("player_close_btn")
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close Player",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        }

        // Playback finished dialogue overlay
        if (hasPlaybackFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp)
                        .testTag("playback_completed_modal"),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playback Complete",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "You just completed Episode ${episode.episodeNumber} of ${episode.seriesTitle}!",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.saveWatchHistory(totalDurationMs, totalDurationMs)
                                        onClosePlayer()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("exit_player_button")
                            ) {
                                Text("Back to Show", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        // Save complete progress of previous episode
                                        viewModel.saveWatchHistory(totalDurationMs, totalDurationMs)
                                        
                                        // Scan local database for the NEXT episode
                                        val seriesEpisodes = viewModel.getEpisodes(episode.seriesTitle)
                                        val nextEp = seriesEpisodes.find { it.episodeNumber == episode.episodeNumber + 1 }
                                        
                                        if (nextEp != null) {
                                            val qualities = viewModel.getQualities(nextEp.id)
                                            val firstQual = qualities.firstOrNull()
                                            if (firstQual != null) {
                                                viewModel.selectEpisode(nextEp)
                                                viewModel.selectQuality(firstQual)
                                                hasPlaybackFinished = false
                                                onNavigateToNextEpisode(nextEp, firstQual)
                                            } else {
                                                onClosePlayer()
                                            }
                                        } else {
                                            onClosePlayer()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("next_episode_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Watch Next", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
