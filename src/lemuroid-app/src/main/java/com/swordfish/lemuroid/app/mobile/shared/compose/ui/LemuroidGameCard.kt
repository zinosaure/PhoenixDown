package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LemuroidGameCard(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { },
    onFavoriteToggle: ((Boolean) -> Unit)? = null,
) {
    val sources = LocalRomSources.current
    val badge = remember(game.fileUri, sources) { resolveSourceName(game.fileUri, sources) }
    ElevatedCard(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            Box {
                LemuroidGameImage(game = game)
                if (badge != null) {
                    SourceBadge(
                        text = badge,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                    )
                }
                if (onFavoriteToggle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp),
                    ) {
                        FavoriteToggle(
                            isToggled = game.isFavorite,
                            onFavoriteToggle = onFavoriteToggle,
                        )
                    }
                }
            }
            LemuroidGameTexts(game = game)
        }
    }
}

/** Retourne un libellé de source lisible depuis le fileUri du jeu. */
fun sourceBadgeFor(fileUri: String): String? {
    return try {
        val uri = Uri.parse(fileUri)
        when (uri.scheme?.lowercase()) {
            "smb" -> uri.host ?: "SMB"
            "content" -> "Local"
            "file" -> "Local"
            else -> null
        }
    } catch (_: Exception) { null }
}

@Composable
fun SourceBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
