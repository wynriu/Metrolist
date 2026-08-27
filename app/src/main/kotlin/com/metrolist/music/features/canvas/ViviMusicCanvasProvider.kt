package com.metrolist.music.features.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Canvas provider backed by ViviMusic's public manifest. */
object ViviMusicCanvasProvider {
    private const val MANIFEST_URL = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
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
    private data class ViviCanvasManifest(
        val items: List<ViviCanvasItem> = emptyList(),
    )

    @Serializable
    private data class ViviCanvasItem(
        val song: String,
        val artist: String,
        val url: String,
        val album: String = "",
    )

    @Volatile
    private var manifest: ViviCanvasManifest? = null
    @Volatile
    private var expiresAtMs = 0L

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String = "",
    ): CanvasArtwork? {
        if (song.isBlank() || artist.isBlank()) return null
        val currentManifest = getManifest() ?: return null
        val songKey = song.normalizeForCanvasComparison()
        val artistKey = artist.normalizeForCanvasComparison()
        val albumKey = album.normalizeForCanvasComparison()
        val target = currentManifest.items.firstOrNull { item ->
            val itemSong = item.song.normalizeForCanvasComparison()
            val itemArtist = item.artist.normalizeForCanvasComparison()
            val itemAlbum = item.album.normalizeForCanvasComparison()
            val albumMatches = albumKey.isBlank() || itemAlbum.isBlank() ||
                itemAlbum == albumKey || itemAlbum.contains(albumKey) || albumKey.contains(itemAlbum)
            (itemSong.contains(songKey) || songKey.contains(itemSong)) &&
                (itemArtist.contains(artistKey) || artistKey.contains(itemArtist)) && albumMatches
        } ?: return null

        return CanvasArtwork(
            name = target.song,
            artist = target.artist,
            albumName = target.album.takeIf(String::isNotBlank),
            animated = target.url,
            videoUrl = target.url,
        )
    }

    private suspend fun getManifest(): ViviCanvasManifest? {
        if (System.currentTimeMillis() < expiresAtMs) return manifest
        return try {
            val response = client.get(MANIFEST_URL)
            if (response.status != HttpStatusCode.OK) return null
            response.body<ViviCanvasManifest>().also {
                manifest = it
                expiresAtMs = System.currentTimeMillis() + CACHE_TTL_MS
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
