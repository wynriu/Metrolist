package com.metrolist.music.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TidalCanvasProvider {
    private const val BASE_URL = "https://api.tidal.com/v1/"
    private const val TIDAL_TOKEN = "vNVdglQOjFJJGG2U"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }
    private data class CacheEntry(val value: CanvasArtwork?, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val countryCode by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
    }

    suspend fun getBySongArtist(song: String, artist: String, album: String? = null): CanvasArtwork? {
        val key = cacheKey("song", song, artist, album.orEmpty())
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }
        val query = listOf(album, artist, song).filter { !it.isNullOrBlank() }.joinToString(" ")
        val result = search(query, "TRACKS", song, artist, null)
        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    private suspend fun search(
        query: String,
        types: String,
        song: String?,
        artist: String?,
        album: String?,
    ): CanvasArtwork? = runCatching {
        val response = client.get("${BASE_URL}search") {
            header("X-Tidal-Token", TIDAL_TOKEN)
            parameter("query", query)
            parameter("limit", "10")
            parameter("types", types)
            parameter("countryCode", countryCode)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        val root = response.body<JsonObject>()
        val section = findSection(root, types.lowercase(Locale.ROOT)) ?: return@runCatching null
        val items = section.jsonObject["items"]?.jsonArray ?: return@runCatching null
        for (item in items) {
            val obj = item.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = obj["artists"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            val returnedArtists = artists.joinToString(", ")
            if (song != null && title.normalizeForCanvasComparison() != song.normalizeForCanvasComparison()) continue
            if (artist != null && !artistMatches(artist, artists)) continue
            if (album != null && types == "ALBUMS" && title.normalizeForCanvasComparison() != album.normalizeForCanvasComparison()) continue

            val albumObject = if (types == "TRACKS") obj["album"]?.jsonObject else obj
            val videoCover = albumObject?.get("videoCover")?.jsonPrimitive?.contentOrNull ?: continue
            val videoUrl = formatVideoUrl(videoCover) ?: continue
            return@runCatching CanvasArtwork(
                name = title,
                artist = returnedArtists.ifBlank { artist },
                albumName = albumObject["title"]?.jsonPrimitive?.contentOrNull,
                videoUrl = videoUrl,
            )
        }
        null
    }.getOrNull()

    private fun findSection(source: JsonElement, key: String): JsonElement? {
        if (source is JsonObject) {
            if (source["items"] is JsonArray) return source
            source[key]?.let { findSection(it, key) }?.let { return it }
            source.values.forEach { findSection(it, key)?.let { return it } }
        } else if (source is JsonArray) {
            source.forEach { findSection(it, key)?.let { return it } }
        }
        return null
    }

    private fun artistMatches(requested: String, returned: List<String>): Boolean {
        val delimiters = Regex("(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)", RegexOption.IGNORE_CASE)
        val requestedArtists = requested.split(delimiters).map { it.normalizeForCanvasComparison() }.filter { it.isNotBlank() }
        val returnedArtists = returned.map { it.normalizeForCanvasComparison() }
        return requestedArtists.isNotEmpty() && requestedArtists.all { wanted -> returnedArtists.any { it == wanted } }
    }

    internal fun formatVideoUrl(id: String): String? {
        val parts = id.split('-')
        if (parts.size != 5) return null
        return "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
    }

    private fun cacheKey(prefix: String, vararg parts: String): String =
        "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
}
