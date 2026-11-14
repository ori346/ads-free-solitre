package com.adfree.solitaire.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.data.Settings
import com.adfree.solitaire.data.ThemeMode
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.game.Suit
import com.adfree.solitaire.ui.CardView
import com.adfree.solitaire.ui.theme.CardBackNames
import com.adfree.solitaire.ui.theme.CardBacks

private val SettingsBodyMaxHeight = 390.dp
private val CardBackPreviewWidth = 42.dp
private val CardBackPreviewHeight = 58.dp

/** Gameplay, appearance, accessibility, and card-back customization dialog. */
@Composable
fun SettingsDialog(
    settings: Settings,
    currentDrawCount: Int,
    dark: Boolean,
    onUpdateSettings: (Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeaderTitle(title = "Settings") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = SettingsBodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsChipRow(
                    title = "Draw mode (applies to next game)",
                    options = DefaultGameConfig.rules.supportedDrawCounts.map { it to "Draw $it" },
                    selected = settings.drawCount,
                    onSelect = { onUpdateSettings(settings.copy(drawCount = it)) },
                    topPadding = 0.dp,
                )
                if (settings.drawCount != currentDrawCount) {
                    Text(
                        text = "Current game is Draw $currentDrawCount. Draw ${settings.drawCount} will apply on the next new deal.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                SettingsToggleRow(
                    title = "4-color deck (♠ ♥ ♦ ♣)",
                    subtitle = "Blue diamonds & green clubs for instant suit clarity",
                    checked = settings.fourColor,
                    onCheckedChange = { onUpdateSettings(settings.copy(fourColor = it)) },
                )

                SettingsChipRow(
                    title = "Theme",
                    options = ThemeMode.entries.map { mode ->
                        mode to mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    selected = settings.theme,
                    onSelect = { onUpdateSettings(settings.copy(theme = it)) },
                )

                SettingsChipRow(
                    title = "Handedness (Deck position)",
                    options = listOf(false to "Right-Handed", true to "Left-Handed"),
                    selected = settings.leftHanded,
                    onSelect = { onUpdateSettings(settings.copy(leftHanded = it)) },
                )

                SettingsToggleRow(
                    title = "Auto-complete when won",
                    subtitle = "Automatically finish once all cards are face-up",
                    checked = settings.autoComplete,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoComplete = it)) },
                    topPadding = 10.dp,
                )

                SettingsToggleRow(
                    title = "Solver assistant",
                    subtitle = "Show the Solver button to check solvability and watch solutions",
                    checked = settings.solverEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(solverEnabled = it)) },
                )

                SettingsToggleRow(
                    title = "Haptic feedback",
                    subtitle = "Gentle vibration on card moves and undo",
                    checked = settings.haptics,
                    onCheckedChange = { onUpdateSettings(settings.copy(haptics = it)) },
                )

                SettingsToggleRow(
                    title = "Debug mode (solvability log)",
                    subtitle = "Print & notify when unsolvable deals are skipped",
                    checked = settings.debugMode,
                    onCheckedChange = { onUpdateSettings(settings.copy(debugMode = it)) },
                )

                Text(
                    "Card back style",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.padding(top = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CardBacks.indices.forEach { i ->
                        val selected = settings.cardBack == i
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onUpdateSettings(settings.copy(cardBack = i)) },
                        ) {
                            CardView(
                                card = Card(Suit.SPADES, Card.RANK_ACE),
                                w = CardBackPreviewWidth,
                                h = CardBackPreviewHeight,
                                back = i,
                                dark = dark,
                                highlight = selected,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = CardBackNames.getOrElse(i) { "" },
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Ad-Free Solitaire v1.0 · 100% Offline · No permissions or tracking.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    topPadding: Dp = 8.dp,
) {
    Row(Modifier.padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> SettingsChipRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    topPadding: Dp = 10.dp,
) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = topPadding))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
