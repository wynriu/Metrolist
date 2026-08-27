package com.metrolist.music.features.comments

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.TimedComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived in-memory cache for highlighted comments.
 * It avoids repeating the same network pagination when the Player is reopened while keeping
 * comment data out of the persistent media caches.
 */
object TimedCommentsRepository {
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val fetchMutex = Mutex()

    suspend fun get(videoId: String): List<TimedComment> = fetchMutex.withLock {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { it.expiresAt > now }?.let { return@withLock it.comments }

        val comments = withContext(Dispatchers.IO) {
            YouTube.featuredComments(videoId).getOrDefault(emptyList())
        }
        cache[videoId] = CacheEntry(comments, now + CACHE_TTL_MS)
        comments
    }

    /** Releases all cached comments when the current track has finished or Player is closed. */
    fun clearAll() {
        cache.clear()
    }

    private data class CacheEntry(
        val comments: List<TimedComment>,
        val expiresAt: Long,
    )
}
