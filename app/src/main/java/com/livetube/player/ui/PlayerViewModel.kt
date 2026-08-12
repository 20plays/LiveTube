package com.livetube.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.livetube.player.playback.Playback
import com.livetube.player.util.Prefs

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val nowPlaying = Playback.nowPlaying

    fun setAudioOnly(value: Boolean) {
        Prefs.setAudioOnly(getApplication(), value)
    }
}