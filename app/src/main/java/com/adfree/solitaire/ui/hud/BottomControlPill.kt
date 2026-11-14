package com.adfree.solitaire.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.GameViewModel
import com.adfree.solitaire.config.AnimationConfig
import com.adfree.solitaire.config.DefaultUiConfig
import com.adfree.solitaire.ui.theme.SolitaireColors
import kotlinx.coroutines.delay

private const val DISABLED_ALPHA = 0.36f
private const val PRESSED_BG_ALPHA = 0.18f
private const val ARROW_STROKE_FRACTION = 0.11f
private const val COMPACT_SCREEN_HEIGHT_DP = 640

private val ActionIconSize = 18.dp
private val DividerHeight = 20.dp
private val DividerWidth = 1.dp
private val ControlBoxShape = RoundedCornerShape(20.dp)
private val ControlItemShape = RoundedCornerShape(12.dp)
private val ControlBoxBottomOffset = 64.dp
private val ControlBoxBottomOffsetWithSolver = 20.dp
private val CompactControlBoxBottomOffset = 12.dp
private val CompactControlBoxBottomOffsetWithSolver = 6.dp

/**
 * Floating bottom control box providing hold-to-repeat Undo/Redo, New Game, Hint,
 * optional interactive Solver row, and the contextual Auto-Complete banner when a win is guaranteed.
 */
@Composable
fun BottomControlPill(
    vm: GameViewModel,
    dark: Boolean,
    animConfig: AnimationConfig = DefaultUiConfig.animation,
    onNewGame: () -> Unit,
) {
    val s = vm.state
    val showSolverRow = vm.settings.solverEnabled
    val pillBg = if (dark) SolitaireColors.BottomPillDark else SolitaireColors.BottomPillLight
    val isCompactHeight = LocalConfiguration.current.screenHeightDp < COMPACT_SCREEN_HEIGHT_DP
    val bottomOffset = when {
        isCompactHeight && showSolverRow -> CompactControlBoxBottomOffsetWithSolver
        isCompactHeight -> CompactControlBoxBottomOffset
        showSolverRow -> ControlBoxBottomOffsetWithSolver
        else -> ControlBoxBottomOffset
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (vm.canAutoComplete) {
            Surface(
                onClick = { vm.autoComplete() },
                color = SolitaireColors.HighlightGold,
                contentColor = SolitaireColors.AutoCompleteContent,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Auto-Complete", modifier = Modifier.size(ActionIconSize))
                    Text("Auto-Complete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Surface(
            color = pillBg,
            shape = ControlBoxShape,
            shadowElevation = 8.dp,
            modifier = Modifier.border(1.dp, SolitaireColors.BottomPillBorder, ControlBoxShape),
        ) {
            Column(
                Modifier
                    .padding(8.dp)
                    .width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepeatStepButton(
                        label = "Undo",
                        isRedo = false,
                        enabled = vm.canUndo,
                        hapticsEnabled = vm.settings.haptics,
                        initialDelayMs = animConfig.repeatButtonInitialDelayMs,
                        stepIntervalMs = animConfig.repeatButtonStepIntervalMs,
                        onStep = { vm.undo() },
                        modifier = Modifier.weight(1f),
                    )
                    PillVerticalDivider()
                    RepeatStepButton(
                        label = "Redo",
                        isRedo = true,
                        enabled = vm.canRedo,
                        hapticsEnabled = vm.settings.haptics,
                        initialDelayMs = animConfig.repeatButtonInitialDelayMs,
                        stepIntervalMs = animConfig.repeatButtonStepIntervalMs,
                        onStep = { vm.redo() },
                        modifier = Modifier.weight(1f),
                    )
                }
                PillHorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillActionButton(
                        icon = Icons.Default.Refresh,
                        label = "New",
                        onClick = onNewGame,
                        modifier = Modifier.weight(1f),
                    )
                    PillVerticalDivider()
                    PillActionButton(
                        icon = Icons.Default.Search,
                        label = "Hint",
                        enabled = !s.isWon && !vm.solverPlaying,
                        highlighted = vm.hint != null,
                        onClick = { vm.showHint() },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showSolverRow) {
                    PillHorizontalDivider()
                    if (vm.solverPlaying) {
                        PillActionButton(
                            icon = Icons.Default.Close,
                            label = "Stop (${vm.solverCurrentStep}/${vm.solverTotalSteps})",
                            enabled = true,
                            highlighted = true,
                            onClick = { vm.stopSolverPlayback() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PillActionButton(
                            icon = Icons.Default.CheckCircle,
                            label = "Solver",
                            enabled = !s.isWon && !vm.autoCompleting,
                            highlighted = vm.solverDialogState != null,
                            onClick = { vm.runSolverCheck() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillVerticalDivider() {
    Box(
        Modifier
            .width(DividerWidth)
            .height(DividerHeight)
            .background(SolitaireColors.BottomPillDivider),
    )
}

@Composable
private fun PillHorizontalDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DividerWidth)
            .background(SolitaireColors.BottomPillDivider),
    )
}

/** Single-tap icon+label action inside [BottomControlPill]. */
@Composable
fun PillActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val tint = if (highlighted && enabled) SolitaireColors.HighlightGold else Color.White.copy(alpha = alpha)
    val itemBg = if (highlighted && enabled) SolitaireColors.HighlightFill else Color.Transparent

    Row(
        modifier
            .clip(ControlItemShape)
            .background(itemBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(ActionIconSize))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (highlighted && enabled) FontWeight.Bold else FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Hold-to-repeat step button used for Undo and Redo actions. */
@Composable
fun RepeatStepButton(
    label: String,
    isRedo: Boolean,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = DefaultUiConfig.animation.repeatButtonInitialDelayMs,
    stepIntervalMs: Long = DefaultUiConfig.animation.repeatButtonStepIntervalMs,
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val currentOnStep by rememberUpdatedState(onStep)
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)

    LaunchedEffect(pressed, enabled, initialDelayMs, stepIntervalMs) {
        if (pressed && enabled) {
            delay(initialDelayMs)
            while (pressed && enabled) {
                if (currentHapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnStep()
                delay(stepIntervalMs)
            }
        }
    }

    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val tint = Color.White.copy(alpha = alpha)
    val itemBg = if (pressed && enabled) Color.White.copy(alpha = PRESSED_BG_ALPHA) else Color.Transparent

    Row(
        modifier
            .clip(ControlItemShape)
            .background(itemBg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onTap = {
                        if (currentHapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnStep()
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        CurvedArrowIcon(tint = tint, mirrorHorizontal = isRedo, modifier = Modifier.size(ActionIconSize))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Vector curved arrow glyph for Undo (left-pointing) and Redo (mirrored right-pointing). */
@Composable
fun CurvedArrowIcon(tint: Color, mirrorHorizontal: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(frac: Float) = if (mirrorHorizontal) w * (1f - frac) else w * frac
        val stroke = Stroke(width = w * ARROW_STROKE_FRACTION, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(x(0.22f), h * 0.36f)
            lineTo(x(0.56f), h * 0.36f)
            cubicTo(
                x(0.86f),
                h * 0.36f,
                x(0.86f),
                h * 0.78f,
                x(0.56f),
                h * 0.78f,
            )
            lineTo(x(0.28f), h * 0.78f)

            moveTo(x(0.40f), h * 0.18f)
            lineTo(x(0.20f), h * 0.36f)
            lineTo(x(0.40f), h * 0.54f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}
