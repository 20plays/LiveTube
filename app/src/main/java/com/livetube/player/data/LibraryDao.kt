package com.livetube.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    fun observeItems(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    suspend fun allItems(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE id = :id")
    fun observeItem(id: String): Flow<LibraryItemEntity?>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun itemById(id: String): LibraryItemEntity?

    @Query("SELECT * FROM cached_streams WHERE itemId = :itemId ORDER BY position")
    fun observeStreams(itemId: String): Flow<List<CachedStreamEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM cached_streams WHERE itemId = :itemId")
    suspend fun maxPosition(itemId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: LibraryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<CachedStreamEntity>)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM cached_streams WHERE itemId = :itemId")
    suspend fun clearStreams(itemId: String)
}