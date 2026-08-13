/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * ArchiveTune provider behaviour adapted from ArchiveTune (GPL-3.0).
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.constants.EnableSimpMusicLyricsKey
import com.metrolist.music.constants.EnableUnisonLyricsKey
import com.metrolist.music.constants.EnableYouLyPlusLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.abs

private object ArchiveTuneLyricsHttp {
    val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }
}

object YouLyPlusLyricsProvider : LyricsProvider {
    override val name = "YouLyPlus"

    private val mirrors =
        listOf(
            "https://lyricsplus.binimum.org/",
            "https://lyricsplus.prjktla.my.id/",
            "https://lyricsplus.prjktla.workers.dev/",
            "https://lyricsplus.atomix.one/",
            "https://lyricsplus-seven.vercel.app/",
        )

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableYouLyPlusLyricsKey] ?: false

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        if (title.isBlank() || artist.isBlank()) {
            return Result.failure(IllegalArgumentException("Song title and artist are required"))
        }

        return try {
            val durationSeconds = duration.takeIf { it > 0 }?.div(1000)
            val lyrics =
                fetchFromMirrors("v1/ttml/get", title, artist, album, durationSeconds) { body ->
                    body.trim().takeIf { it.startsWith("<") }
                        ?: ArchiveTuneLyricsHttp.json.parseToJsonElement(body).firstString("ttml")
                            ?.takeIf { it.trim().startsWith("<") }
                } ?: fetchFromMirrors("v2/lyrics/get", title, artist, album, durationSeconds) { body ->
                    ArchiveTuneLyricsHttp.json.parseToJsonElement(body).asLyricsText()
                } ?: throw IllegalStateException("Lyrics unavailable")
            Result.success(lyrics)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetchFromMirrors(
        path: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
        decode: (String) -> String?,
    ): String? {
        mirrors.forEach { baseUrl ->
            try {
                val response =
                    ArchiveTuneLyricsHttp.client.get(baseUrl + path) {
                        parameter("title", title.trim())
                        parameter("artist", artist.trim())
                        album?.trim()?.takeIf(String::isNotEmpty)?.let { parameter("album", it) }
                        durationSeconds?.takeIf { it > 0 }?.let { parameter("duration", it) }
                    }
                if (!response.status.isSuccess()) return@forEach
                decode(response.bodyAsText())?.takeIf(String::isNotBlank)?.let { return it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Try the next public mirror; callers receive a failure only after all mirrors fail.
            }
        }
        return null
    }
}

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"
    private const val apiBaseUrl = "https://api-lyrics.simpmusic.org/v1/"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableSimpMusicLyricsKey] ?: false

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        try {
            require(id.isNotBlank()) { "A video id is required" }
            val response = ArchiveTuneLyricsHttp.client.get(apiBaseUrl + id)
            if (!response.status.isSuccess()) throw IllegalStateException("Lyrics unavailable")

            val tracks = ArchiveTuneLyricsHttp.json.parseToJsonElement(response.bodyAsText()).dataArray()
            val durationSeconds = duration.takeIf { it > 0 }?.div(1000) ?: 0
            val bestTrack =
                tracks.minByOrNull { track ->
                    abs((track.jsonObject["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) - durationSeconds)
                } ?: throw IllegalStateException("Lyrics unavailable")
            val lyrics =
                bestTrack.jsonObject.firstString("syncedLyrics", "plainLyrics")
                    ?: throw IllegalStateException("Lyrics unavailable")
            Result.success(lyrics)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
}

object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"
    private const val apiBaseUrl = "https://unison.boidu.dev/"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableUnisonLyricsKey] ?: false

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        try {
            val durationSeconds = duration.takeIf { it > 0 }?.div(1000)
            val metadataResult =
                fetch("lyrics/search") {
                    parameter("song", title.trim())
                    parameter("artist", artist.trim())
                    album?.trim()?.takeIf(String::isNotEmpty)?.let { parameter("album", it) }
                    durationSeconds?.takeIf { it > 0 }?.let { parameter("duration", it) }
                    parameter("limit", 1)
                }
            val lyrics = metadataResult.firstString("lyrics") ?: fetch("lyrics") { parameter("v", id) }.firstString("lyrics")
            Result.success(lyrics ?: throw IllegalStateException("Lyrics unavailable"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun fetch(
        path: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): JsonElement {
        val response = ArchiveTuneLyricsHttp.client.get(apiBaseUrl + path, configure)
        if (!response.status.isSuccess()) return JsonObject(emptyMap())
        return ArchiveTuneLyricsHttp.json.parseToJsonElement(response.bodyAsText())
    }
}

private fun JsonElement.firstString(vararg keys: String): String? =
    when (this) {
        is JsonObject -> {
            keys.firstNotNullOfOrNull { key -> (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                ?: values.firstNotNullOfOrNull { it.firstString(*keys) }
        }
        is JsonArray -> firstNotNullOfOrNull { it.firstString(*keys) }
        else -> null
    }

private fun JsonElement.dataArray(): JsonArray =
    ((this as? JsonObject)?.get("data") as? JsonArray) ?: JsonArray(emptyList())

private fun JsonElement.asLyricsText(): String? {
    firstString("ttml")?.takeIf { it.trim().startsWith("<") }?.let { return it }
    firstString("syncedLyrics", "plainLyrics", "lyrics")?.takeIf(String::isNotBlank)?.let { return it }

    val lines = (this as? JsonObject)?.get("lyrics") as? JsonArray ?: return null
    return lines
        .mapNotNull { line ->
            val data = line as? JsonObject ?: return@mapNotNull null
            val text = data["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val time = data["time"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            if (time == null) text else "[${time.toLrcTimestamp()}]$text"
        }.joinToString("\n")
        .takeIf(String::isNotBlank)
}

private fun Long.toLrcTimestamp(): String {
    val safeTime = coerceAtLeast(0L)
    val minutes = safeTime / 60_000L
    val seconds = (safeTime % 60_000L) / 1_000L
    val milliseconds = safeTime % 1_000L
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds)
}
