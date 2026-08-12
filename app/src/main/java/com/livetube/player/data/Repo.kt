package com.livetube.player.data

import android.content.Context
import android.util.Base64
import com.livetube.player.extractor.Yt
import com.livetube.player.util.UserMessageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object Repo {

    private lateinit var db: AppDatabase

    fun init(context: Context) {
        db = AppDatabase.get(context)
    }

    fun dao(): LibraryDao = db.dao()

    suspend fun addByUrl(raw: String): LibraryItemEntity = withContext(Dispatchers.IO) {
        val item = Yt.resolveNew(raw.trim())
        val entity = LibraryItemEntity(
            id = item.id,
            kind = item.kind,
            url = item.url,
            name = item.name,
            thumbnail = item.thumbnail,
            pageBlob = serializePage(item.page),
            addedAt = System.currentTimeMillis(),
        )
        dao().upsertItem(entity)
        dao().clearStreams(entity.id)
        dao().insertStreams(item.items.mapIndexed { i, row ->
            row.toEntity(entity.id, i)
        })
        entity
    }

    suspend fun refresh(id: String) = withContext(Dispatchers.IO) {
        val existing = dao().itemById(id) ?: return@withContext
        val fresh = Yt.resolveFirstPage(existing.kind, existing.url)
        dao().clearStreams(id)
        dao().insertStreams(fresh.items.mapIndexed { i, row -> row.toEntity(id, i) })
        dao().upsertItem(
            existing.copy(
                name = fresh.name.takeIf { it.isNotBlank() } ?: existing.name,
                thumbnail = fresh.thumbnail ?: existing.thumbnail,
                pageBlob = serializePage(fresh.page),
            ),
        )
    }

    suspend fun refreshAll(): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val items = dao().allItems()
        var ok = 0
        val failed = mutableListOf<String>()
        for (item in items) {
            try {
                refresh(item.id)
                ok++
            } catch (e: Exception) {
                failed.add(item.name)
            }
            delay(400)
        }
        ok to failed
    }

    suspend fun loadMore(id: String): Int = withContext(Dispatchers.IO) {
        val item = dao().itemById(id) ?: return@withContext 0
        val page = deserializePage(item.pageBlob) ?: return@withContext 0
        val result = Yt.fetchNext(item.kind, item.url, page)
        if (result.rows.isEmpty()) {
            dao().upsertItem(item.copy(pageBlob = null))
            return@withContext 0
        }
        val start = dao().maxPosition(id) + 1
        dao().insertStreams(result.rows.mapIndexed { i, row -> row.toEntity(id, start + i) })
        dao().upsertItem(item.copy(pageBlob = serializePage(result.page)))
        result.rows.size
    }

    suspend fun loadAll(id: String, onProgress: (Int) -> Unit) {
        while (true) {
            val added = loadMore(id)
            if (added == 0) break
            onProgress(added)
            delay(200)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao().clearStreams(id)
        dao().deleteItem(id)
    }

    private fun Yt.StreamRow.toEntity(itemId: String, position: Int) = CachedStreamEntity(
        itemId = itemId,
        position = position,
        title = title,
        videoUrl = videoUrl,
        thumbnail = thumb,
        durationSec = durationSec,
        isLive = isLive,
    )

    private fun serializePage(page: Page?): String? {
        if (page == null) return null
        return try {
            ByteArrayOutputStream().use { bos ->
                ObjectOutputStream(bos).use { it.writeObject(page) }
                Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deserializePage(blob: String?): Page? {
        if (blob == null) return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            ByteArrayInputStream(bytes).use { bis ->
                ObjectInputStream(bis).use { it.readObject() as? Page }
            }
        } catch (e: Exception) {
            null
        }
    }
}