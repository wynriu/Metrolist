package com.metrolist.music.features.comments

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.TimedComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived in-memory cache for timestamped comments.
 * It avoids repeating the same network pagination when the Player is reopened while keeping
 * comment data out of the persistent media caches.
 */
object TimedCommentsRepository {
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun get(videoId: String): List<TimedComment> {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { it.expiresAt > now }?.let { return it.comments }

        val comments = withContext(Dispatchers.IO) {
            YouTube.timedComments(videoId).getOrDefault(emptyList())
        }
        cache[videoId] = CacheEntry(comments, now + CACHE_TTL_MS)
        return comments
    }

    private data class CacheEntry(
        val comments: List<TimedComment>,
        val expiresAt: Long,
    )
}
