package com.livetube.player.extractor

import com.livetube.player.util.UserMessageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.TimeUnit

object Yt {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; XXXXX Build/TKQ1.221013.002) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
                .addInterceptor { chain ->
                    val original = chain.request()
                    val withUa = original.newBuilder()
                        .header("User-Agent", UA)
                        .build()
                    chain.proceed(withUa)
                }
                .build()
            NewPipe.init(OkHttpDownloader(client))
            NewPipe.setPreferredLocalization(Localization.DEFAULT)
            NewPipe.setPreferredContentCountry(ContentCountry("US"))
            initialized = true
        }
    }

    data class StreamRow(
        val title: String,
        val videoUrl: String,
        val thumb: String?,
        val durationSec: Long,
        val isLive: Boolean,
    )

    data class NewItem(
        val id: String,
        val kind: String,
        val url: String,
        val name: String,
        val thumbnail: String?,
        val items: List<StreamRow>,
        val page: Page?,
    )

    data class StreamPlay(val url: String, val live: Boolean, val title: String)

    data class DownloadStream(
        val url: String,
        val title: String,
        val mimeType: String,
        val extension: String,
    )

    val service: StreamingService get() = ServiceList.YouTube

    fun identify(raw: String): String {
        val s = ServiceList.YouTube
        return when {
            s.playlistLHFactory.acceptUrl(raw) -> "playlist"
            s.channelLHFactory.acceptUrl(raw) -> "channel"
            else -> "video"
        }
    }

    suspend fun resolveNew(raw: String): NewItem = withContext(Dispatchers.IO) {
        try {
            when (identify(raw)) {
                "channel" -> resolveChannel(raw)
                "playlist" -> resolvePlaylist(raw)
                else -> throw UserMessageException("That link isn't a channel or playlist URL.")
            }
        } catch (e: UserMessageException) {
            throw e
        } catch (e: ExtractionException) {
            throw UserMessageException(e.message ?: "Couldn't read that link.")
        } catch (e: java.io.IOException) {
            throw UserMessageException("Couldn't reach YouTube. Check your connection.")
        } catch (e: RuntimeException) {
            android.util.Log.e("LT", "resolveNew failed", e)
            throw UserMessageException("Couldn't add that link. Try again in a moment.")
        }
    }

    private fun resolveChannel(raw: String): NewItem {
        val ce = service.getChannelExtractor(raw)
        ce.fetchPage()
        val id = headerSafe { ce.id } ?: raw
        val name = headerSafe { ce.name } ?: fallbackName(raw)
        val thumbnail = headerSafe { firstImageUrl(ce.avatars) }

        var tabUrl: String
        val tabExtractor: ChannelTabExtractor = try {
            val videosTab = ce.tabs.firstOrNull { it.url?.endsWith("/videos") == true }
                ?: ce.tabs.firstOrNull { it.url?.endsWith("/streams") == true }
                ?: throw ParsingException("No video tab found for this channel.")
            tabUrl = absoluteUrl(videosTab.url)
            val extractor = service.getChannelTabExtractor(videosTab)
            if (videosTab !is ReadyChannelTabListLinkHandler) {
                extractor.fetchPage()
            }
            extractor
        } catch (e: Exception) {
            tabUrl = tabUrlFor(raw)
            val extractor =
                service.getChannelTabExtractor(service.getChannelTabLHFactory().fromUrl(tabUrl))
            extractor.fetchPage()
            extractor
        }

        val page = tabExtractor.getInitialPage()
        val rows = page.items.filterIsInstance<StreamInfoItem>().map { it.toRow() }
        return NewItem(id, "channel", tabUrl, name, thumbnail, rows, page.nextPage)
    }

    private fun tabUrlFor(raw: String): String {
        val cleaned = raw.substringBefore('?').trimEnd('/')
        if (cleaned.endsWith("/videos") || cleaned.endsWith("/streams")) {
            return absoluteUrl(cleaned)
        }
        return absoluteUrl(cleaned) + "/videos"
    }

    private fun fallbackName(raw: String): String {
        val part = raw.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return part.removePrefix("@").ifBlank { "Channel" }
    }

    private inline fun <T> headerSafe(block: () -> T): T? =
        try {
            block()
        } catch (_: ParsingException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    private fun absoluteUrl(url: String?): String {
        if (url.isNullOrEmpty()) return "https://www.youtube.com"
        return if (url.startsWith("http")) url else "https://www.youtube.com$url"
    }

    private fun resolvePlaylist(raw: String): NewItem {
        val pe = service.getPlaylistExtractor(raw)
        pe.fetchPage()
        val page = pe.getInitialPage()
        val rows = page.items.filterIsInstance<StreamInfoItem>().map { it.toRow() }
        return NewItem(
            id = pe.id,
            kind = "playlist",
            url = pe.url,
            name = pe.name,
            thumbnail = firstImageUrl(pe.thumbnails),
            items = rows,
            page = page.nextPage,
        )
    }

    suspend fun resolveFirstPage(kind: String, url: String): NewItem = withContext(Dispatchers.IO) {
        if (kind == "channel") {
            val tabExtractor = channelTabExtractorFor(url)
            val page = tabExtractor.getInitialPage()
            NewItem(
                id = headerSafe { tabExtractor.id } ?: url,
                kind = "channel",
                url = url,
                name = headerSafe { tabExtractor.name } ?: "",
                thumbnail = null,
                items = page.items.filterIsInstance<StreamInfoItem>().map { it.toRow() },
                page = page.nextPage,
            )
        } else {
            val pe = service.getPlaylistExtractor(url)
            pe.fetchPage()
            val page = pe.getInitialPage()
            NewItem(
                id = pe.id,
                kind = "playlist",
                url = url,
                name = pe.name,
                thumbnail = firstImageUrl(pe.thumbnails),
                items = page.items.filterIsInstance<StreamInfoItem>().map { it.toRow() },
                page = page.nextPage,
            )
        }
    }

    data class NextPage(val rows: List<StreamRow>, val page: Page?)

    private fun channelTabExtractorFor(tabUrl: String): ChannelTabExtractor {
        return try {
            val ce = service.getChannelExtractor(tabUrl)
            ce.fetchPage()
            val videosTab = ce.tabs.firstOrNull { it.url?.endsWith("/videos") == true }
                ?: ce.tabs.firstOrNull { it.url?.endsWith("/streams") == true }
                ?: throw ParsingException("No video tab found for this channel.")
            val extractor = service.getChannelTabExtractor(videosTab)
            if (videosTab !is ReadyChannelTabListLinkHandler) {
                extractor.fetchPage()
            }
            extractor
        } catch (e: Exception) {
            val extractor =
                service.getChannelTabExtractor(service.getChannelTabLHFactory().fromUrl(tabUrl))
            extractor.fetchPage()
            extractor
        }
    }

    suspend fun fetchNext(kind: String, url: String, page: Page): NextPage =
        withContext(Dispatchers.IO) {
            val extractor = if (kind == "channel") {
                channelTabExtractorFor(url)
            } else {
                service.getPlaylistExtractor(url)
            }
            val next = extractor.getPage(page)
            NextPage(
                next.items.filterIsInstance<StreamInfoItem>().map { it.toRow() },
                next.nextPage,
            )
        }

    private fun isLiveType(streamType: StreamType): Boolean =
        streamType == StreamType.LIVE_STREAM ||
            streamType == StreamType.AUDIO_LIVE_STREAM ||
            streamType == StreamType.POST_LIVE_STREAM ||
            streamType == StreamType.POST_LIVE_AUDIO_STREAM

    suspend fun resolveStream(videoUrl: String, audioOnly: Boolean): StreamPlay =
        withContext(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(service.getStreamExtractor(videoUrl))
                val live = isLiveType(info.streamType)
                if (live) {
                    val liveUrl = info.hlsUrl.takeIf { !it.isNullOrBlank() }
                        ?: info.videoOnlyStreams.firstOrNull()?.url
                        ?: throw UserMessageException("No streamable live URL found.")
                    return@withContext StreamPlay(liveUrl, true, info.name)
                }
                if (audioOnly) {
                    val original = info.audioStreams.filter {
                        it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
                    }
                    val candidates = if (original.isEmpty()) {
                        info.audioStreams.filter { it.audioTrackType != AudioTrackType.DUBBED }
                    } else {
                        original
                    }
                    val bestAudio = candidates.maxByOrNull {
                        maxOf(it.averageBitrate, it.bitrate).takeIf { b -> b > 0 } ?: 0
                    }
                    val audioUrl = bestAudio?.getUrl()
                    if (audioUrl.isNullOrEmpty()) {
                        throw UserMessageException("No audio stream found.")
                    }
                    return@withContext StreamPlay(audioUrl, false, info.name)
                }
                val combined = info.videoStreams.filter { !it.isVideoOnly }
                val best = combined.maxByOrNull { parseResolution(it.resolution) }
                val videoUrlResolved = best?.url
                    ?: info.dashMpdUrl.takeIf { !it.isNullOrBlank() }
                    ?: info.videoStreams.firstOrNull()?.url
                    ?: throw UserMessageException("No video stream found.")
                return@withContext StreamPlay(videoUrlResolved, false, info.name)
            } catch (e: UserMessageException) {
                throw e
            } catch (e: ExtractionException) {
                throw UserMessageException(e.message ?: "Couldn't prepare playback.")
            } catch (e: java.io.IOException) {
                throw UserMessageException("Couldn't reach YouTube. Check your connection.")
            } catch (e: RuntimeException) {
                android.util.Log.e("LT", "resolveStream failed", e)
                throw UserMessageException("Couldn't prepare playback. YouTube may have changed.")
            }
        }

    suspend fun resolveDownload(videoUrl: String): DownloadStream =
        withContext(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(service.getStreamExtractor(videoUrl))
                if (isLiveType(info.streamType)) {
                    throw UserMessageException("Live streams can't be downloaded yet.")
                }

                val best = info.videoStreams
                    .asSequence()
                    .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
                    .maxByOrNull { parseResolution(it.resolution) }
                    ?: throw UserMessageException(
                        "No single-file video stream is available for this video.",
                    )

                val resolvedUrl = best.url
                    ?: throw UserMessageException("The selected video stream has no download URL.")
                val format = best.format
                DownloadStream(
                    url = resolvedUrl,
                    title = info.name,
                    mimeType = format?.mimeType ?: "video/mp4",
                    extension = format?.suffix ?: "mp4",
                )
            } catch (e: UserMessageException) {
                throw e
            } catch (e: ExtractionException) {
                throw UserMessageException(e.message ?: "Couldn't prepare the download.")
            } catch (e: java.io.IOException) {
                throw UserMessageException("Couldn't reach YouTube. Check your connection.")
            } catch (e: RuntimeException) {
                android.util.Log.e("LT", "resolveDownload failed", e)
                throw UserMessageException("Couldn't prepare the download. YouTube may have changed.")
            }
        }

    private fun parseResolution(resolution: String): Int =
        resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    private fun firstImageUrl(images: List<Image>): String? = images.firstOrNull()?.url

    private fun StreamInfoItem.toRow() = StreamRow(
        title = name,
        videoUrl = url,
        thumb = firstImageUrl(thumbnails),
        durationSec = duration,
        isLive = streamType == StreamType.LIVE_STREAM,
    )

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(req: org.schabi.newpipe.extractor.downloader.Request): Response {
            val builder = Request.Builder().url(req.url())
            req.headers().forEach { (name, values) ->
                values.forEach { builder.header(name, it) }
            }
            val body = req.dataToSend()
            val okBody = body?.toRequestBody(null)
            builder.method(req.httpMethod(), okBody)
            val resp = client.newCall(builder.build()).execute()
            resp.use { r ->
                return Response(
                    r.code,
                    r.message,
                    r.headers.toMultimap(),
                    r.body?.string() ?: "",
                    r.request.url.toString(),
                )
            }
        }
    }
}