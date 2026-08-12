package com.livetube.player.util

import android.content.Context

class UserMessageException(message: String) : Exception(message)

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

object Prefs {
    private const val FILE = "livetube"

    fun audioOnly(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean("audio_only", true)

    fun setAudioOnly(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("audio_only", value)
            .apply()
    }

    fun autoNext(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean("auto_next", false)

    fun setAutoNext(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_next", value)
            .apply()
    }
}