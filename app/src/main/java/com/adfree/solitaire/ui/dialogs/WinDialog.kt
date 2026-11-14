package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adfree.solitaire.ui.hud.formatTime

private const val RANK_FIRST = 1
private const val RANK_SECOND = 2
private const val RANK_THIRD = 3
private const val SUMMARY_CARD_ALPHA = 0.5f

private val WinLogoSize = 36.dp
private val SummaryCornerRadius = 10.dp

/** Victory summary dialog celebrating a completed deal, score, time, streak, and leaderboard rank. */
@Composable
fun WinDialog(
    score: Int,
    moves: Int,
    elapsed: Long,
    streak: Int,
    rank: Int?,
    solvedBySolver: Boolean = false,
    onPlayAgain: () -> Unit,
    onTopScores: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (solvedBySolver) {
                DialogHeaderTitle(
                    title = "Solution Complete",
                    subtitle = "Solved by Solver · Recorded as a loss",
                    logoSize = WinLogoSize,
                )
            } else {
                DialogHeaderTitle(title = "You Won! 🎉", logoSize = WinLogoSize)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!solvedBySolver && rank != null) {
                    val badge = when (rank) {
                        RANK_FIRST -> "🏆 New #1 High Score!"
                        RANK_SECOND -> "🥈 #2 on Top Scores!"
                        RANK_THIRD -> "🥉 #3 on Top Scores!"
                        else -> "⭐ Ranked #$rank on Top Scores!"
                    }
                    Text(badge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Surface(
                    shape = RoundedCornerShape(SummaryCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SUMMARY_CARD_ALPHA),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StatRow("Final Score", score.toString())
                        StatRow("Total Moves", moves.toString())
                        StatRow("Time", formatTime(elapsed))
                        if (!solvedBySolver) {
                            StatRow("Win Streak", "$streak 🔥")
                        } else {
                            StatRow("Result", "Loss (Solver Used)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onPlayAgain) {
                Text(if (solvedBySolver) "New Deal" else "Play Again", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onTopScores) { Text("Top Scores") }
                TextButton(onDismiss) { Text("Close") }
            }
        },
    )
}
