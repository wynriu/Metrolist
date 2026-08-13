/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * ArchiveTune canvas behaviour adapted from ArchiveTune (GPL-3.0).
 */

package com.metrolist.music.ui.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class ArchiveTuneCanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val animatedVertical: String? = null,
) {
    val preferredUrl: String?
        get() = animatedVertical ?: animated ?: static
}

private data class CanvasCacheEntry(
    val url: String?,
    val expiresAtMillis: Long,
)

object ArchiveTuneCanvasClient {
    private const val primaryBaseUrl = "https://artwork-archivetune.koiiverse.cloud/"
    private const val fallbackBaseUrl = "https://artwork.boidu.dev/"
    private const val cacheTtlMillis = 60_000L

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val cache = ConcurrentHashMap<String, CanvasCacheEntry>()

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    suspend fun getArtworkUrl(
        title: String,
        artist: String,
        storefront: String = "us",
    ): String? {
        if (title.isBlank() || artist.isBlank()) return null

        val cacheKey = "${title.normalized()}|${artist.normalized()}|$storefront"
        cache[cacheKey]?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }?.let { return it.url }
        cache.remove(cacheKey)

        val artwork =
            fetch(primaryBaseUrl, title, artist, storefront)
                ?: fetch(fallbackBaseUrl, title, artist, storefront)
        val url = artwork?.takeIf { it.matches(title, artist) }?.preferredUrl
        cache[cacheKey] = CanvasCacheEntry(url, System.currentTimeMillis() + cacheTtlMillis)
        return url
    }

    private suspend fun fetch(
        baseUrl: String,
        title: String,
        artist: String,
        storefront: String,
    ): ArchiveTuneCanvasArtwork? =
        try {
            val response =
                client.get(baseUrl) {
                    parameter("s", title.trim())
                    parameter("a", artist.trim())
                    parameter("storefront", storefront)
                }
            if (!response.status.isSuccess()) return null
            json.decodeFromString<ArchiveTuneCanvasArtwork>(response.bodyAsText())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun ArchiveTuneCanvasArtwork.matches(title: String, artist: String): Boolean {
        val normalizedTitle = title.normalized()
        val normalizedArtist = artist.normalized()
        return name?.normalized()?.contains(normalizedTitle) != false &&
            this.artist?.normalized()?.contains(normalizedArtist) != false
    }

    private fun String.normalized(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
