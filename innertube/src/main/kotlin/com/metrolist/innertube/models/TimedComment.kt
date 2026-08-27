package com.metrolist.innertube.models

/**
 * A YouTube user comment containing a timestamp embedded in its text.
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
 * Removes a leading or inline timestamp from a comment and returns its playback position.
 * Supports mm:ss and hh:mm:ss forms, for example `0:20 Hay quá` and `1:02:15 Hay`.
 */
fun String.extractEmbeddedTimestamp(): Pair<Long, String>? {
    val match = Regex("(?<!\\d)(?:(\\d{1,2}):)?(\\d{1,3}):([0-5]\\d)(?!\\d)").find(this) ?: return null
    val first = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull()
    val second = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val totalSeconds = if (first == null) {
        second * 60L + seconds
    } else {
        first * 3600L + second * 60L + seconds
    }
    val remaining = replaceRange(match.range, "")
        .trim()
        .trimStart('-', ':', '–', '—')
        .trim()
    if (remaining.isBlank()) return null
    return totalSeconds * 1000L to remaining
}
