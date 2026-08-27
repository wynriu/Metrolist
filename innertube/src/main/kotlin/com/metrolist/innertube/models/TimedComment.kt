package com.metrolist.innertube.models

/**
 * A YouTube user comment containing an embedded playback timestamp.
 * The timestamp is normalized to milliseconds for direct comparison with ExoPlayer position.
 */
data class TimedComment(
    val id: String,
    val timestampMs: Long,
    val text: String,
    val avatarUrl: String?,
)

/**
 * Returns a comparison-friendly form for matching comment text without changing what is shown.
 */
fun String.normalizedCommentText(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Extracts every YouTube-style playback timestamp from a comment.
 *
 * YouTube accepts both `mm:ss` and `hh:mm:ss` markers, and one comment can contain several
 * markers, such as `0:00 Intro, 1:15 Chorus`. Each marker is returned with the text that follows
 * it so the caller can display all available moments instead of keeping only the first one.
 */
fun String.extractEmbeddedTimestamps(): List<Pair<Long, String>> {
    val matches =
        Regex("(?<!\\d)(?:(\\d{1,2}):)?(\\d{1,3}):([0-5]\\d)(?!\\d)")
            .findAll(this)
            .toList()
    if (matches.isEmpty()) return emptyList()

    return matches.mapIndexedNotNull { index, match ->
        val first = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull()
        val second = match.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
        val seconds = match.groupValues[3].toLongOrNull() ?: return@mapIndexedNotNull null
        val totalSeconds =
            if (first == null) {
                second * 60L + seconds
            } else {
                first * 3600L + second * 60L + seconds
            }
        val nextStart = matches.getOrNull(index + 1)?.range?.first ?: length
        val remaining =
            substring(match.range.last + 1, nextStart)
                .trim()
                .trimStart('-', ':', '–', '—')
                .trim()
                .trimEnd(',', ';', '|')
                .trim()
        if (remaining.isBlank()) return@mapIndexedNotNull null
        totalSeconds * 1000L to remaining
    }
}

/**
 * Backward-compatible helper for callers that need only the first marker.
 */
fun String.extractEmbeddedTimestamp(): Pair<Long, String>? =
    extractEmbeddedTimestamps().firstOrNull()
