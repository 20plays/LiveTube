package com.livetube.player.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> Playback.togglePlayPause()
            ACTION_STOP -> Playback.stop()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.livetube.player.ACTION_TOGGLE"
        const val ACTION_STOP = "com.livetube.player.ACTION_STOP"
    }
}