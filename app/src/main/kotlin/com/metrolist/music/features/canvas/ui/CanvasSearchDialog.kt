package com.metrolist.music.features.canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.R
import com.metrolist.music.constants.CanvasSource
import com.metrolist.music.features.canvas.CanvasArtworkSelectionStore
import com.metrolist.music.features.canvas.CanvasSearchRepository
import com.metrolist.music.features.canvas.CanvasSearchResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasSearchDialog(
    mediaId: String,
    initialSong: String,
    initialArtist: String,
    initialAlbum: String,
    onDismiss: () -> Unit,
) {
    var song by remember(mediaId) { mutableStateOf(initialSong) }
    var artist by remember(mediaId) { mutableStateOf(initialArtist) }
    var album by remember(mediaId) { mutableStateOf(initialAlbum) }
    var results by remember(mediaId) { mutableStateOf<List<CanvasSearchResult>>(emptyList()) }
    var isSearching by remember(mediaId) { mutableStateOf(false) }
    var hasSearched by remember(mediaId) { mutableStateOf(false) }
    var selectedSource by remember(mediaId) { mutableStateOf<CanvasSource?>(null) }
    val scope = rememberCoroutineScope()

    val visibleResults = remember(results, selectedSource) {
        results.filter { selectedSource == null || it.source == selectedSource }
    }

    fun search() {
        if (song.isBlank() || artist.isBlank() || isSearching) return
        scope.launch {
            isSearching = true
            hasSearched = true
            results = CanvasSearchRepository.search(song.trim(), artist.trim(), album.trim())
            isSearching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            LazyColumn(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.canvas_search_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.find_canvas_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = song,
                        onValueChange = { song = it },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.canvas_search_song)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.canvas_search_artist)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.canvas_search_album)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                        }
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = ::search,
                            enabled = song.isNotBlank() && artist.isNotBlank() && !isSearching,
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(androidx.compose.ui.res.stringResource(R.string.canvas_search_button))
                            }
                        }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = isSearching,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.canvas_search_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (results.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedSource == null,
                                onClick = { selectedSource = null },
                                label = { Text(androidx.compose.ui.res.stringResource(R.string.canvas_source)) },
                            )
                            results.map { it.source }.distinct().forEach { source ->
                                FilterChip(
                                    selected = selectedSource == source,
                                    onClick = { selectedSource = source },
                                    label = { Text(sourceLabel(source)) },
                                )
                            }
                        }
                    }
                }
                if (hasSearched && !isSearching && visibleResults.isEmpty()) {
                    item {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.canvas_search_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(
                    items = visibleResults,
                    key = { _, result -> "${result.source.name}:${result.artwork.preferredAnimationUrl}" },
                ) { _, result ->
                    CanvasSearchResultCard(
                        result = result,
                        onSelect = {
                            CanvasArtworkSelectionStore.put(mediaId, result.artwork)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasSearchResultCard(
    result: CanvasSearchResult,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = sourceLabel(result.source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large),
            ) {
                CanvasArtworkPlayer(
                    primaryUrl = result.artwork.preferredAnimationUrl,
                    fallbackUrl = result.artwork.animatedTall,
                    isPlaying = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = result.artwork.name.orEmpty().ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = result.artwork.artist.orEmpty().ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.canvas_search_apply),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun sourceLabel(source: CanvasSource): String = when (source) {
    CanvasSource.AUTO -> androidx.compose.ui.res.stringResource(R.string.canvas_source_auto)
    CanvasSource.APPLE_MUSIC -> androidx.compose.ui.res.stringResource(R.string.canvas_source_apple_music)
    CanvasSource.TIDAL -> androidx.compose.ui.res.stringResource(R.string.canvas_source_tidal)
    CanvasSource.BETTER_LYRICS -> androidx.compose.ui.res.stringResource(R.string.canvas_source_better_lyrics)
    CanvasSource.ARCHIVE_TUNE -> androidx.compose.ui.res.stringResource(R.string.canvas_source_archive_tune)
    CanvasSource.VIVIMUSIC -> androidx.compose.ui.res.stringResource(R.string.canvas_source_vivi_music)
}
