package com.metrolist.music.ui.player

import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import java.util.Locale

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = primaryUrl?.takeIf(String::isNotBlank)
    val fallback = fallbackUrl?.takeIf(String::isNotBlank)
    val initialUrl = primary ?: fallback ?: return
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var ready by remember(initialUrl) { mutableStateOf(false) }

    val dataSourceFactory = remember {
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(OkHttpClient()))
    }
    val player = remember(initialUrl, dataSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                playWhenReady = isPlaying
            }
    }

    DisposableEffect(player, primary, fallback) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (currentUrl == primary && !fallback.isNullOrBlank()) {
                    currentUrl = fallback
                    ready = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, player) {
        val uri = currentUrl.trim()
        val lower = uri.lowercase(Locale.ROOT)
        val mime = when {
            lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.contains(".mp4") -> MimeTypes.VIDEO_MP4
            primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
            else -> MimeTypes.VIDEO_MP4
        }
        ready = false
        player.setMediaItem(MediaItem.Builder().setUri(uri).setMimeType(mime).build())
        player.prepare()
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying, player) {
        player.playWhenReady = isPlaying
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val alpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(300),
        label = "canvasArtworkAlpha",
    )

    AndroidView(
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val textureView = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                addView(textureView)
                player.setVideoTextureView(textureView)
            }
        },
        modifier = modifier.fillMaxSize().alpha(alpha),
    )
}


@Composable
fun CanvasArtworkOverlay(
    title: String,
    artist: String,
    album: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    if (title.isBlank() || artist.isBlank()) return
    val enabled by com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.constants.CanvasThumbnailAnimationKey,
        defaultValue = true,
    )
    if (!enabled) return
    val source by com.metrolist.music.utils.rememberEnumPreference(
        com.metrolist.music.constants.CanvasSourceKey,
        defaultValue = com.metrolist.music.constants.CanvasSource.AUTO,
    )
    var artwork by remember(title, artist, album, source) { mutableStateOf<com.metrolist.music.canvas.CanvasArtwork?>(null) }
    val storefront = remember {
        java.util.Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(java.util.Locale.ROOT) ?: "us"
    }

    LaunchedEffect(title, artist, album, source) {
        val key = listOf(source.name, title, artist, album, storefront).joinToString("|") { it.trim().lowercase(java.util.Locale.ROOT) }
        CanvasArtworkCache.get(key)?.let {
            artwork = it
            return@LaunchedEffect
        }
        val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (source) {
                com.metrolist.music.constants.CanvasSource.AUTO ->
                    com.metrolist.music.canvas.AppleMusicCanvasProvider.getBySongArtist(title, artist, album, storefront)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: com.metrolist.music.canvas.TidalCanvasProvider.getBySongArtist(title, artist, album)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: com.metrolist.music.canvas.ViviMusicCanvasProvider.getBySongArtist(title, artist, album)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                com.metrolist.music.constants.CanvasSource.APPLE_MUSIC ->
                    com.metrolist.music.canvas.AppleMusicCanvasProvider.getBySongArtist(title, artist, album, storefront)
                com.metrolist.music.constants.CanvasSource.TIDAL ->
                    com.metrolist.music.canvas.TidalCanvasProvider.getBySongArtist(title, artist, album)
                com.metrolist.music.constants.CanvasSource.VIVIMUSIC ->
                    com.metrolist.music.canvas.ViviMusicCanvasProvider.getBySongArtist(title, artist, album)
            }
        }
        if (fetched != null) {
            CanvasArtworkCache.put(key, fetched)
            artwork = fetched
        }
    }

    artwork?.let {
        CanvasArtworkPlayer(
            primaryUrl = it.preferredAnimationUrl ?: it.animatedTall,
            fallbackUrl = it.animatedTall,
            isPlaying = isPlaying,
            modifier = modifier,
        )
    }
}

private object CanvasArtworkCache {
    private const val MAX_SIZE = 24
    private val cache = object : LinkedHashMap<String, com.metrolist.music.canvas.CanvasArtwork>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.metrolist.music.canvas.CanvasArtwork>?): Boolean = size > MAX_SIZE
    }

    @Synchronized fun get(key: String): com.metrolist.music.canvas.CanvasArtwork? = cache[key]
    @Synchronized fun put(key: String, value: com.metrolist.music.canvas.CanvasArtwork) { cache[key] = value }
}
