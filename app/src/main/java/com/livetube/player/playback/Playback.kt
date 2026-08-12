package com.livetube.player.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.livetube.player.extractor.Yt
import com.livetube.player.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Playback {

    data class NowPlaying(val title: String, val live: Boolean)

    data class QueueItem(val watchUrl: String, val title: String, val live: Boolean)

    lateinit var player: ExoPlayer
        private set

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = ArrayDeque<QueueItem>()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _watchUrl = MutableStateFlow<String?>(null)
    val watchUrl = _watchUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val uaRequest = request.newBuilder()
                    .header("User-Agent", "LiveTube/1.0 (Android)")
                    .build()
                chain.proceed(uaRequest)
            }
            .build()
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(OkHttpDataSource.Factory(client)),
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (queue.isNotEmpty() && autoNextEnabled()) {
                        scope.launch { advanceQueue() }
                    } else {
                        _nowPlaying.value = null
                    }
                }
            }
        })
        scope.launch { monitorLiveStream() }
    }

    fun play(watchUrl: String, resolvedUrl: String, title: String, live: Boolean) {
        play(watchUrl, resolvedUrl, title, live, emptyList())
    }

    fun play(
        watchUrl: String,
        resolvedUrl: String,
        title: String,
        live: Boolean,
        remaining: List<QueueItem>,
    ) {
        queue.clear()
        queue.addAll(remaining)
        startItem(watchUrl, resolvedUrl, title, live)
    }

    fun restartWith(
        watchUrl: String,
        resolvedUrl: String,
        title: String,
        live: Boolean,
        positionMs: Long,
    ) {
        _watchUrl.value = watchUrl
        _nowPlaying.value = NowPlaying(title, live)
        player.setMediaItem(MediaItem.fromUri(resolvedUrl))
        player.prepare()
        player.seekTo(positionMs)
        player.play()
        startService()
    }

    private fun startItem(watchUrl: String, resolvedUrl: String, title: String, live: Boolean) {
        _watchUrl.value = watchUrl
        _nowPlaying.value = NowPlaying(title, live)
        player.setMediaItem(MediaItem.fromUri(resolvedUrl))
        player.prepare()
        player.play()
        startService()
    }

    private fun autoNextEnabled(): Boolean {
        val ctx = appContext ?: return false
        return queue.isNotEmpty() && Prefs.autoNext(ctx)
    }

    private suspend fun advanceQueue() {
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            try {
                val audioOnly = appContext?.let { Prefs.audioOnly(it) } ?: false
                val resolved = Yt.resolveStream(next.watchUrl, audioOnly)
                startItem(next.watchUrl, resolved.url, next.title, next.live)
                return
            } catch (_: Exception) {
                // skip videos that can't be resolved and try the next one
            }
        }
        _nowPlaying.value = null
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        player.stop()
        queue.clear()
        _nowPlaying.value = null
    }

    private fun startService() {
        val ctx = appContext ?: return
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
    }

    private suspend fun monitorLiveStream() {
        while (true) {
            delay(4 * 60_000)
            val np = _nowPlaying.value ?: continue
            if (!np.live) continue
            val watch = _watchUrl.value ?: continue
            try {
                val res = Yt.resolveStream(watch, audioOnly = false)
                val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (res.url != currentUri) {
                    val position = player.currentPosition
                    player.setMediaItem(MediaItem.fromUri(res.url))
                    player.prepare()
                    player.seekTo(position)
                    player.play()
                }
            } catch (_: Exception) {
                // keep playing with the old manifest; retry next cycle
            }
        }
    }
}