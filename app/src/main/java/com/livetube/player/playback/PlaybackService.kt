package com.livetube.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.livetube.player.R
import com.livetube.player.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession.Builder(this, Playback.player).build()
        scope.launch {
            combine(Playback.nowPlaying, Playback.isPlaying) { np, playing -> np to playing }
                .collect { (np, playing) ->
                    if (np != null) {
                        notificationManager().notify(NOTIFICATION_ID, buildMediaNotification(np.title, playing))
                    } else {
                        notificationManager().cancel(NOTIFICATION_ID)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundIfNeeded()
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    private fun startForegroundIfNeeded() {
        if (!Playback.player.playWhenReady) return
        val notification = buildMediaNotification(
            Playback.nowPlaying.value?.title ?: "LiveTube",
            Playback.isPlaying.value,
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // already foregrounded by Media3, or transient error
        }
    }

    private fun buildMediaNotification(title: String, playing: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val toggleIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, PlaybackActionReceiver::class.java).setAction(PlaybackActionReceiver.ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, PlaybackActionReceiver::class.java).setAction(PlaybackActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText("LiveTube")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play",
                toggleIntent,
            )
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }

    private fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(this, NotificationManager::class.java)!!

    private fun createNotificationChannel() {
        val manager = notificationManager()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val np = Playback.nowPlaying.value
        if (np == null || !Playback.player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        scope.cancel()
        super.onDestroy()
    }
}