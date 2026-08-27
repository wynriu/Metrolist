package com.metrolist.music.features.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Canvas provider backed by ArchiveTune's artwork service. */
object ArchiveTuneCanvasProvider {
    private const val ARTWORK_URL = "https://artwork-archivetune.koiiverse.cloud/"
    private const val CACHE_TTL_MS = 60_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            expectSuccess = false
        }
    }

    @Serializable
    private data class ArchiveTuneArtwork(
        val name: String? = null,
        val artist: String? = null,
        val albumName: String? = null,
        val animated: String? = null,
        val animatedVertical: String? = null,
        val videoUrl: String? = null,
        val videoUrlVertical: String? = null,
    )

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String = "",
        storefront: String = "us",
    ): CanvasArtwork? {
        if (song.isBlank() || artist.isBlank()) return null
        val key = listOf(song, artist, album, storefront)
            .joinToString("|") { it.trim().lowercase(Locale.ROOT) }
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val value = try {
            val response = client.get(ARTWORK_URL) {
                parameter("s", song)
                parameter("a", artist)
                parameter("storefront", storefront)
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<ArchiveTuneArtwork>()
                    .toCanvasArtwork()
                    ?.takeIf { it.matchesCanvasIdentity(song, artist) }
            } else {
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        cache[key] = CacheEntry(value, System.currentTimeMillis() + CACHE_TTL_MS)
        return value
    }

    private fun ArchiveTuneArtwork.toCanvasArtwork(): CanvasArtwork? {
        val mainAnimated = animated ?: videoUrl
        val tallAnimated = animatedVertical ?: videoUrlVertical
        if (mainAnimated.isNullOrBlank() && tallAnimated.isNullOrBlank()) return null
        return CanvasArtwork(
            name = name,
            artist = artist,
            albumName = albumName,
            animated = mainAnimated,
            videoUrl = videoUrl,
            animatedTall = tallAnimated,
        )
    }

    private fun CanvasArtwork.matchesCanvasIdentity(song: String, artist: String): Boolean =
        name?.normalizeForCanvasComparison() == song.normalizeForCanvasComparison() &&
            this.artist?.normalizeForCanvasComparison() == artist.normalizeForCanvasComparison()
}
