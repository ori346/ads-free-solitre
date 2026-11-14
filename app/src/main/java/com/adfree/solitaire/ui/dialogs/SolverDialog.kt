package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.SolverDialogState

private const val CARD_SURFACE_ALPHA = 0.5f
private val ProgressIndicatorSize = 26.dp
private val ProgressStrokeWidth = 3.dp
private val InfoCardCornerRadius = 10.dp
private val SolverDialogMaxBodyHeight = 320.dp

/**
 * Dialog presenting live solver progress, solvability outcome, and actions to either
 * watch the step-by-step solution (for solvable boards) or restart/redeal (for unsolvable boards).
 */
@Composable
fun SolverDialog(
    dialogState: SolverDialogState,
    canRestartDeal: Boolean,
    activeStreak: Int,
    onWatchSolution: () -> Unit,
    onRestartDeal: () -> Unit,
    onNewDeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialogState) {
        SolverDialogState.Checking -> SolverCheckingDialog(onCancel = onDismiss)
        is SolverDialogState.Solvable -> SolverSolvableDialog(
            moveCount = dialogState.winningMoves.size,
            activeStreak = activeStreak,
            onWatchSolution = onWatchSolution,
            onKeepPlaying = onDismiss,
        )
        is SolverDialogState.Unsolvable -> SolverUnsolvableDialog(
            canRestartDeal = canRestartDeal,
            activeStreak = activeStreak,
            onRestartDeal = onRestartDeal,
            onNewDeal = onNewDeal,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SolverCheckingDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { DialogHeaderTitle(title = "Running Solver…") },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ProgressIndicatorSize),
                    strokeWidth = ProgressStrokeWidth,
                )
                Text(
                    text = "Analyzing the current board configuration to see if a winning solution exists…",
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SolverSolvableDialog(
    moveCount: Int,
    activeStreak: Int,
    onWatchSolution: () -> Unit,
    onKeepPlaying: () -> Unit,
) {
    val lossWarning = if (activeStreak > 0) {
        "Watching the solution counts as a loss and will end your $activeStreak-game win streak."
    } else {
        "Watching the solution counts as a loss in your statistics."
    }

    AlertDialog(
        onDismissRequest = onKeepPlaying,
        title = { DialogHeaderTitle(title = "Board is Solvable! ✨") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SolverDialogMaxBodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Good news! This board can still be won in $moveCount move${if (moveCount == 1) "" else "s"}.",
                    fontSize = 14.sp,
                )
                Text(
                    text = "Would you like to keep playing on your own, or watch the solver play out the solution step by step?",
                    fontSize = 14.sp,
                )
                SolverWarningCard(text = lossWarning)
            }
        },
        confirmButton = {
            TextButton(onClick = onWatchSolution) {
                Text("Watch Solution", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepPlaying) {
                Text("Keep Playing")
            }
        },
    )
}

@Composable
private fun SolverUnsolvableDialog(
    canRestartDeal: Boolean,
    activeStreak: Int,
    onRestartDeal: () -> Unit,
    onNewDeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prompt = if (canRestartDeal) {
        "This board configuration is no longer solvable. Would you like to restart this deal from the beginning (which is solvable) or deal a new game?"
    } else {
        "No winning solution was found for this board configuration. Would you like to start a fresh deal?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeaderTitle(title = "No Solution Found") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SolverDialogMaxBodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = prompt, fontSize = 14.sp)
                if (activeStreak > 0) {
                    SolverWarningCard(
                        text = "Restarting or dealing a new game will end your $activeStreak-game win streak.",
                    )
                }
            }
        },
        confirmButton = {
            if (canRestartDeal) {
                TextButton(onClick = onRestartDeal) {
                    Text("Restart Deal", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onNewDeal) {
                    Text("New Deal", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (canRestartDeal) {
                    TextButton(onClick = onNewDeal) {
                        Text("New Deal")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun SolverWarningCard(text: String) {
    Surface(
        shape = RoundedCornerShape(InfoCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_SURFACE_ALPHA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
        )
    }
}
