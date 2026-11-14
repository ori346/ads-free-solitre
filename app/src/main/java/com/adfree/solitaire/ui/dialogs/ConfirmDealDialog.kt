package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirmation dialog shown before abandoning or restarting an in-progress deal. */
@Composable
fun ConfirmDealDialog(
    canUndo: Boolean,
    activeStreak: Int,
    onNewDeal: () -> Unit,
    onRestartDeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when {
        !canUndo -> "Start a fresh deal?"
        activeStreak > 0 ->
            "Start a fresh deal or replay the current deal from the beginning? Doing so will end your $activeStreak-game win streak."
        else -> "Start a fresh deal or replay the current deal from the beginning?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Options") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onNewDeal) { Text("New Deal") }
        },
        dismissButton = {
            Row {
                if (canUndo) {
                    TextButton(onRestartDeal) { Text("Restart Deal") }
                }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}
