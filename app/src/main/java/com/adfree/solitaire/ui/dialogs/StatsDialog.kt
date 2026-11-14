package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.data.ScoreEntry
import com.adfree.solitaire.data.Stats
import com.adfree.solitaire.ui.components.SolitaireLogo
import com.adfree.solitaire.ui.hud.formatScoreDate
import com.adfree.solitaire.ui.hud.formatTime

private const val TAB_SCORES = 0
private const val TAB_SUMMARY = 1
private const val PODIUM_RANK_LIMIT = 3
private const val ROW_CARD_ALPHA = 0.45f
private val MedalBadges = listOf("🥇", "🥈", "🥉")

private val DefaultDialogLogoSize = 32.dp
private val ScoresListMaxHeight = 320.dp
private val ScoreCardCornerRadius = 10.dp
private val RankBadgeWidth = 30.dp

/** Shared dialog title header pairing [SolitaireLogo] with a bold title and optional subtitle. */
@Composable
internal fun DialogHeaderTitle(
    title: String,
    logoSize: Dp = DefaultDialogLogoSize,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SolitaireLogo(size = logoSize)
        if (subtitle != null) {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(title, fontWeight = FontWeight.Bold)
        }
    }
}

/** Shared two-column metric row used across statistics and victory dialogs. */
@Composable
internal fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Tabbed dialog presenting the Top Scores leaderboard and lifetime player statistics. */
@Composable
fun StatsDialog(
    stats: Stats,
    initialTab: Int = TAB_SCORES,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogHeaderTitle(title = "Leaderboard & Stats")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == TAB_SCORES,
                        onClick = { tab = TAB_SCORES },
                        label = { Text("Top ${DefaultGameConfig.rules.leaderboardSize} Scores") },
                    )
                    FilterChip(
                        selected = tab == TAB_SUMMARY,
                        onClick = { tab = TAB_SUMMARY },
                        label = { Text("Summary") },
                    )
                }
            }
        },
        text = {
            if (tab == TAB_SCORES) {
                TopScoresList(stats.topScores)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = ScoresListMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatRow("Games played", stats.played.toString())
                    StatRow("Games won", "${stats.won} (${stats.winPercentage}%)")
                    StatRow("Best score", stats.bestScore.toString())
                    StatRow("Best time", if (stats.bestTimeSec > 0) formatTime(stats.bestTimeSec) else "-")
                    StatRow("Current streak", stats.streak.toString())
                    StatRow("Best streak", stats.bestStreak.toString())
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onResetStats) { Text("Reset All") } },
    )
}

/** Ranked list of recorded top-scoring wins. */
@Composable
fun TopScoresList(scores: List<ScoreEntry>) {
    if (scores.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No top scores yet.\nWin a game to get on the board!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = ScoresListMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        scores.forEachIndexed { idx, entry ->
            val rank = idx + 1
            val medal = MedalBadges.getOrNull(idx) ?: "#$rank"
            val isPodium = rank <= PODIUM_RANK_LIMIT
            Surface(
                shape = RoundedCornerShape(ScoreCardCornerRadius),
                color = if (isPodium) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = ROW_CARD_ALPHA)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ROW_CARD_ALPHA)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            medal,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isPodium) 18.sp else 14.sp,
                            modifier = Modifier.width(RankBadgeWidth),
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "${entry.score} pts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    "Draw ${entry.drawCount}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val details = buildList {
                                if (entry.moves > 0) add("${entry.moves} moves")
                                if (entry.timeSec > 0) add(formatTime(entry.timeSec))
                            }.joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(
                                    details,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        formatScoreDate(entry.dateMillis),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
