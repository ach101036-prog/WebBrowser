package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DownloadedVideoEntity>>

    @Query(
        "SELECT * FROM downloaded_videos WHERE title LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' OR sourcePageUrl LIKE '%' || :query || '%' ORDER BY createdAtMillis DESC"
    )
    fun search(query: String): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<DownloadedVideoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedVideoEntity): Long

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
