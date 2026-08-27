/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.features.canvas

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

/** Persistent on-disk cache for Canvas HLS manifests, segments, and MP4 media. */
object CanvasMediaCache {
    private const val DIRECTORY_NAME = "canvas"
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    private fun cacheFor(context: Context): SimpleCache =
        cache ?: synchronized(lock) {
            cache ?: SimpleCache(
                File(context.filesDir, DIRECTORY_NAME),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context),
            ).also { cache = it }
        }

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream =
            OkHttpDataSource.Factory(OkHttpClient())
                .setUserAgent("Metrolist/Canvas")
        return CacheDataSource.Factory()
            .setCache(cacheFor(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun cacheSpace(context: Context): Long = cacheFor(context).cacheSpace

    fun clear(context: Context) {
        val canvasCache = cacheFor(context)
        synchronized(lock) {
            canvasCache.keys.forEach(canvasCache::removeResource)
        }
    }
}
