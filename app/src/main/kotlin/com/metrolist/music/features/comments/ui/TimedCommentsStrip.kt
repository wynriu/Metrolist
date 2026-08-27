package com.metrolist.music.features.comments.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.features.comments.TimedCommentsRepository
import com.metrolist.innertube.models.TimedComment

@Composable
fun TimedCommentsStrip(
    videoId: String?,
    positionMs: Long,
    enabled: Boolean,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    var comments by remember(videoId) { mutableStateOf<List<TimedComment>>(emptyList()) }

    LaunchedEffect(enabled, videoId) {
        comments = emptyList()
        if (!enabled || videoId.isNullOrBlank()) return@LaunchedEffect
        comments = TimedCommentsRepository.get(videoId)
    }

    val activeComment = remember(comments, positionMs) {
        comments.lastOrNull { it.timestampMs <= positionMs }
    }

    if (!enabled || activeComment == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CommentAvatar(
            avatarUrl = activeComment.avatarUrl,
            modifier = Modifier.size(34.dp),
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                targetState = activeComment,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                },
                label = "timedCommentTransition",
            ) { comment ->
                Text(
                    text = comment.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        velocity = 35.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CommentAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (avatarUrl.isNullOrBlank()) {
        Icon(
            painter = painterResource(R.drawable.person),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .clip(CircleShape)
                .padding(4.dp),
        )
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    }
}
