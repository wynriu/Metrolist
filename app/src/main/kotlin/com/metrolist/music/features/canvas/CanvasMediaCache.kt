/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.features.canvas

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File
import java.util.TreeSet

/** Persistent on-disk cache for Canvas HLS manifests, segments, and MP4 media. */
object CanvasMediaCache {
    const val DEFAULT_MAX_CACHE_SIZE_MB = 256

    private const val DIRECTORY_NAME = "canvas"
    private const val TEMP_DIRECTORY_NAME = "canvas-playback"

    private val lock = Any()
    private val httpClient by lazy(::OkHttpClient)

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var temporaryCache: SimpleCache? = null

    @Volatile
    private var configuredSizeMb: Int? = null

    @Volatile
    private var evictor: DynamicLruCacheEvictor? = null

    private fun cacheDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY_NAME)

    private fun temporaryCacheDirectory(context: Context): File =
        File(context.cacheDir, TEMP_DIRECTORY_NAME)

    private fun upstreamDataSourceFactory(): DataSource.Factory =
        OkHttpDataSource.Factory(httpClient)
            .setUserAgent("Metrolist/Canvas")

    /**
     * Applies a new persistent cache size without replacing an open SimpleCache. A value of zero
     * is handled by [disable], while active Canvas playback uses [temporaryDataSourceFactory].
     */
    fun configure(context: Context, maxSizeMb: Int) {
        require(maxSizeMb > 0) { "Canvas cache must be disabled through disable()" }
        synchronized(lock) {
            releaseTemporaryLocked(context)
            cache?.let { existingCache ->
                if (configuredSizeMb != maxSizeMb) {
                    evictor?.setMaxBytes(maxSizeMb * 1024L * 1024L, existingCache)
                    configuredSizeMb = maxSizeMb
                }
                return
            }

            val newEvictor = DynamicLruCacheEvictor(maxSizeMb * 1024L * 1024L)
            cache = SimpleCache(
                cacheDirectory(context),
                newEvictor,
                StandaloneDatabaseProvider(context),
            )
            evictor = newEvictor
            configuredSizeMb = maxSizeMb
        }
    }

    fun dataSourceFactory(
        context: Context,
        maxSizeMb: Int = DEFAULT_MAX_CACHE_SIZE_MB,
    ): DataSource.Factory {
        if (maxSizeMb <= 0) {
            disable(context)
            return temporaryDataSourceFactory(context)
        }

        configure(context, maxSizeMb)
        val canvasCache = synchronized(lock) { cache }
            ?: return upstreamDataSourceFactory()

        return CacheDataSource.Factory()
            .setCache(canvasCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Returns a small cache-backed datasource for the currently playing Canvas when persistent
     * caching is disabled. This is not part of the user cache and is cleared on track changes,
     * playback end, or player disposal.
     */
    private fun temporaryDataSourceFactory(context: Context): DataSource.Factory {
        val playbackCache = synchronized(lock) {
            temporaryCache ?: SimpleCache(
                temporaryCacheDirectory(context),
                DynamicLruCacheEvictor(Long.MAX_VALUE),
                StandaloneDatabaseProvider(context),
            ).also { temporaryCache = it }
        }

        return CacheDataSource.Factory()
            .setCache(playbackCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun cacheSpace(context: Context): Long = synchronized(lock) {
        cache?.cacheSpace ?: 0L
    }

    fun clear(context: Context) {
        synchronized(lock) {
            cache?.let(::clearLocked) ?: cacheDirectory(context).deleteRecursively()
            clearTemporaryLocked(context)
        }
    }

    /**
     * Disables persistent Canvas caching while keeping a separate temporary cache available for
     * the Canvas that is currently playing.
     */
    fun disable(context: Context) {
        synchronized(lock) {
            disableLocked(context)
        }
    }

    /** Removes the current playback-only Canvas files but keeps the temporary cache available. */
    fun clearTemporary(context: Context) {
        synchronized(lock) {
            clearTemporaryLocked(context)
        }
    }

    /** Releases and deletes all playback-only Canvas files when the player leaves composition. */
    fun releaseTemporary(context: Context) {
        synchronized(lock) {
            releaseTemporaryLocked(context)
        }
    }

    private fun disableLocked(context: Context) {
        cache?.let {
            clearLocked(it)
            it.release()
        }
        cache = null
        evictor = null
        configuredSizeMb = null
        cacheDirectory(context).deleteRecursively()
    }

    private fun clearTemporaryLocked(context: Context) {
        temporaryCache?.let(::clearLocked)
    }

    private fun releaseTemporaryLocked(context: Context) {
        temporaryCache?.let {
            clearLocked(it)
            it.release()
        }
        temporaryCache = null
        temporaryCacheDirectory(context).deleteRecursively()
    }

    private fun clearLocked(canvasCache: SimpleCache) {
        canvasCache.keys.toList().forEach(canvasCache::removeResource)
    }

    private class DynamicLruCacheEvictor(
        initialMaxBytes: Long,
    ) : CacheEvictor {
        private var maxBytes = initialMaxBytes
        private var currentSize = 0L
        private val leastRecentlyUsed =
            TreeSet<CacheSpan> { first, second ->
                val timestampComparison = first.lastTouchTimestamp.compareTo(second.lastTouchTimestamp)
                if (timestampComparison != 0) timestampComparison else first.compareTo(second)
            }

        override fun requiresCacheSpanTouches(): Boolean = true

        override fun onCacheInitialized() = Unit

        @Synchronized
        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != -1L) evictCache(cache, length)
        }

        @Synchronized
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            leastRecentlyUsed.add(span)
            currentSize += span.length
            evictCache(cache, 0L)
        }

        @Synchronized
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            leastRecentlyUsed.remove(span)
            currentSize -= span.length
        }

        @Synchronized
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        @Synchronized
        fun setMaxBytes(newMaxBytes: Long, cache: Cache) {
            maxBytes = newMaxBytes
            evictCache(cache, 0L)
        }

        private fun evictCache(cache: Cache, incomingLength: Long) {
            while (currentSize + incomingLength > maxBytes && leastRecentlyUsed.isNotEmpty()) {
                cache.removeSpan(leastRecentlyUsed.first())
            }
        }
    }
}
