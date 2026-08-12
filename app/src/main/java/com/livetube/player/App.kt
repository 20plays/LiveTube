package com.livetube.player

import android.app.Application
import com.livetube.player.data.Repo
import com.livetube.player.extractor.Yt
import com.livetube.player.playback.Playback

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Yt.init()
        Repo.init(this)
        Playback.init(this)
    }
}