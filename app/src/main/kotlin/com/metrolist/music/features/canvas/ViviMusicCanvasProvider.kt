package com.metrolist.music.features.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ViviCanvasManifest(val items: List<ViviCanvasItem> = emptyList())

@Serializable
private data class ViviCanvasItem(
    val song: String,
    val artist: String,
    val url: String,
    val album: String = "",
)

object ViviMusicCanvasProvider {
    private const val MANIFEST_URL = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
    private const val CACHE_TTL_MS = 60_000L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = false
        }
    }
    @Volatile private var manifest: ViviCanvasManifest? = null
    @Volatile private var expiresAt: Long = 0L

    suspend fun getBySongArtist(song: String, artist: String, album: String): CanvasArtwork? {
        if (song.isBlank() || artist.isBlank()) return null
        val current = getManifest() ?: return null
        val songKey = song.normalizeForCanvasComparison()
        val artistKey = artist.normalizeForCanvasComparison()
        val albumKey = album.normalizeForCanvasComparison()
        val item = current.items.firstOrNull {
            val itemSong = it.song.normalizeForCanvasComparison()
            val itemArtist = it.artist.normalizeForCanvasComparison()
            val itemAlbum = it.album.normalizeForCanvasComparison()
            val albumMatches = albumKey.isBlank() || itemAlbum.isBlank() ||
                itemAlbum == albumKey || itemAlbum.contains(albumKey) || albumKey.contains(itemAlbum)
            (itemSong.contains(songKey) || songKey.contains(itemSong))
                && (itemArtist.contains(artistKey) || artistKey.contains(itemArtist))
                && albumMatches
        } ?: return null
        return CanvasArtwork(
            name = item.song,
            artist = item.artist,
            albumName = item.album.ifBlank { null },
            animated = item.url,
            videoUrl = item.url,
        )
    }

    private suspend fun getManifest(): ViviCanvasManifest? {
        if (System.currentTimeMillis() < expiresAt) return manifest
        return runCatching {
            val response = client.get(MANIFEST_URL)
            if (response.status != HttpStatusCode.OK) return@runCatching null
            response.body<ViviCanvasManifest>().also {
                manifest = it
                expiresAt = System.currentTimeMillis() + CACHE_TTL_MS
            }
        }.getOrNull()
    }
}
