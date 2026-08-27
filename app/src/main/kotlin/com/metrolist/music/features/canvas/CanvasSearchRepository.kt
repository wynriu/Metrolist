package com.metrolist.music.features.canvas

import com.metrolist.music.constants.CanvasSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Result returned by the Player's manual Canvas picker. */
data class CanvasSearchResult(
    val source: CanvasSource,
    val artwork: CanvasArtwork,
)

object CanvasSearchRepository {
    suspend fun search(
        song: String,
        artist: String,
        album: String,
        storefront: String = "us",
    ): List<CanvasSearchResult> = withContext(Dispatchers.IO) {
        if (song.isBlank() || artist.isBlank()) return@withContext emptyList()
        val providers: List<Pair<CanvasSource, suspend () -> CanvasArtwork?>> = listOf(
            CanvasSource.APPLE_MUSIC to suspend {
                AppleMusicCanvasProvider.getBySongArtist(song, artist, album, storefront)
            },
            CanvasSource.BETTER_LYRICS to suspend {
                BetterLyricsCanvasProvider.getBySongArtist(song, artist, storefront)
            },
        )
        coroutineScope {
            providers.map { (source, fetch) ->
                async {
                    try {
                        fetch()
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                            ?.let { CanvasSearchResult(source, it) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
