package com.adfree.solitaire.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.ui.components.SolitaireLogo
import com.adfree.solitaire.ui.theme.SolitaireColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60L
private const val SCORE_DATE_PATTERN = "MMM d, yyyy"
private const val LEGACY_SCORE_DATE_FALLBACK = "Previous Best"
private val ScoreDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(SCORE_DATE_PATTERN, Locale.US)

private val HudLogoSize = 28.dp
private val IconButtonSize = 36.dp
private val IconButtonGlyphSize = 19.dp
private val BorderWidth = 1.dp

/** Formats elapsed seconds as `m:ss`. */
fun formatTime(sec: Long): String =
    String.format(Locale.US, "%d:%02d", sec / SECONDS_PER_MINUTE, sec % SECONDS_PER_MINUTE)

/** Formats a leaderboard timestamp in milliseconds, falling back for legacy migrated records. */
fun formatScoreDate(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (millis <= 0L) return LEGACY_SCORE_DATE_FALLBACK
    return ScoreDateFormatter.format(Instant.ofEpochMilli(millis).atZone(zoneId))
}

/**
 * Top HUD status bar displaying the emblem, live score/moves/time pill, and utility actions.
 */
@Composable
fun TopHudBar(
    score: Int,
    moves: Int,
    elapsed: Long,
    dark: Boolean,
    onShowScores: () -> Unit,
    onShowHelp: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val pillBg = if (dark) SolitaireColors.HudPillDark else SolitaireColors.HudPillLight
    val pillBorder = SolitaireColors.HudPillBorder

    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f, fill = false)
                .padding(end = 6.dp)
                .clip(CircleShape)
                .background(pillBg)
                .border(BorderWidth, pillBorder, CircleShape)
                .clickable(onClick = onShowScores)
                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SolitaireLogo(size = HudLogoSize)
            HudMetric("SCORE", score.toString())
            HudMetric("MOVES", moves.toString())
            HudMetric("TIME", formatTime(elapsed))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopIconButton(
                icon = Icons.Default.Star,
                contentDescription = "Top Scores & Stats",
                tint = SolitaireColors.HighlightGold,
                bg = pillBg,
                border = pillBorder,
                onClick = onShowScores,
            )
            TopIconButton(
                icon = Icons.Default.Info,
                contentDescription = "How to Play",
                tint = SolitaireColors.HudHelpIconTint,
                bg = pillBg,
                border = pillBorder,
                onClick = onShowHelp,
            )
            TopIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                bg = pillBg,
                border = pillBorder,
                onClick = onShowSettings,
            )
        }
    }
}

/** Compact label/value pair inside the top HUD pill. */
@Composable
fun HudMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = SolitaireColors.HudLabelText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Circular icon button matching the top HUD pill chrome. */
@Composable
fun TopIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    bg: Color,
    border: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(IconButtonSize)
            .clip(CircleShape)
            .background(bg)
            .border(BorderWidth, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(IconButtonGlyphSize))
    }
}
