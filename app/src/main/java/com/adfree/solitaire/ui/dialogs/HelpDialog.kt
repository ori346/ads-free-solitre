package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HelpLogoSize = 34.dp
private val HelpBodyMaxHeight = 360.dp

/** Built-in Klondike Solitaire rules, controls, and scoring guide dialog. */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeaderTitle(
                title = "How to Play",
                subtitle = "Klondike Solitaire · Ad-Free",
                logoSize = HelpLogoSize,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = HelpBodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HelpItem(
                    title = "🎯 Goal",
                    body = "Move all 52 cards to the 4 Foundation piles at the top, building each suit up from Ace (A) to King (K).",
                )
                HelpItem(
                    title = "🃏 Building Columns",
                    body = "In the 7 columns below, stack cards in descending order (K, Q, J, 10 … 2) while alternating Red (♥ ♦) and Black (♠ ♣) suits. Empty columns can only be filled with a King.",
                )
                HelpItem(
                    title = "👆 Easy One-Tap Controls",
                    body = "Tap any playable card (or anywhere on a movable stack) to automatically send it to the best legal spot! You can also drag and drop cards.",
                )
                HelpItem(
                    title = "⏪ Fast Undo & Redo",
                    body = "Tap Undo/Redo to step one move at a time, or press and hold to quickly rewind through your move history.",
                )
                HelpItem(
                    title = "🧩 Board Solver",
                    body = "Stuck? Tap Solver to check if the current board is still winnable. If it is, you can keep playing or watch the step-by-step solution (counts as a loss).",
                )
                HelpItem(
                    title = "🏆 Scoring",
                    body = "+10 pts to Foundation · +5 pts from Waste to Tableau · +5 pts for turning over a hidden card.",
                )
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Got It", fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun HelpItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}
