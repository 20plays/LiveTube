package com.livetube.player.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.livetube.player.extractor.Yt

object Downloads {

    fun enqueue(context: Context, download: Yt.DownloadStream): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val filename = "${sanitizeFilename(download.title)}.${download.extension}"
        val request = DownloadManager.Request(Uri.parse(download.url))
            .setTitle(download.title)
            .setDescription("Saving to Downloads")
            .setMimeType(download.mimeType)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        return manager.enqueue(request)
    }

    private fun sanitizeFilename(title: String): String {
        val cleaned = buildString {
            title.forEach { char ->
                append(
                    if (char.isLetterOrDigit() || char in " ._()-[]") {
                        char
                    } else {
                        '_'
                    },
                )
            }
        }.trim().trim('.').take(120)
        return cleaned.ifBlank { "LiveTube video" }
    }
}