package com.example.data.local

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val dao: BookmarkDao) {
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observeIsBookmarked(url: String): Flow<Boolean> = dao.observeIsBookmarked(url)

    suspend fun toggleBookmark(title: String, url: String, currentlyBookmarked: Boolean) {
        if (url.isBlank() || url.startsWith("about:")) return
        if (currentlyBookmarked) {
            dao.deleteByUrl(url)
        } else {
            dao.insert(
                BookmarkEntity(
                    title = title.ifBlank { url }.take(60),
                    url = url,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteById(id)
}
