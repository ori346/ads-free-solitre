package com.adfree.solitaire.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.config.AnimationConfig
import com.adfree.solitaire.config.DefaultUiConfig
import com.adfree.solitaire.ui.theme.SolitaireColors
import kotlin.math.sin

private const val CONFETTI_SEED_MULT = 73
private const val CONFETTI_SEED_OFFSET = 19
private const val CONFETTI_SEED_MOD = 100
private const val CONFETTI_BASE_SPEED = 0.5f
private const val CONFETTI_SPEED_BUCKETS = 4
private const val CONFETTI_SPEED_STEP = 0.1f
private const val CONFETTI_X_MULT = 29
private const val CONFETTI_X_OFFSET = 7
private const val CONFETTI_X_MOD = 97
private const val CONFETTI_SWAY_FREQ = 4f
private const val CONFETTI_SWAY_AMPLITUDE = 12f
private const val CONFETTI_BASE_RADIUS = 5f
private const val CONFETTI_RADIUS_STEP = 1.5f
private const val CONFETTI_RADIUS_BUCKETS = 3

/** Renders celebratory falling confetti particles across the full board on game victory. */
@Composable
fun WinConfettiOverlay(animConfig: AnimationConfig = DefaultUiConfig.animation) {
    val transition = rememberInfiniteTransition(label = "winConfetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animConfig.confettiCycleDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiFall",
    )
    val colors = SolitaireColors.ConfettiColors
    Canvas(Modifier.fillMaxSize()) {
        val count = animConfig.confettiParticleCount
        for (i in 0 until count) {
            val seed = (i * CONFETTI_SEED_MULT + CONFETTI_SEED_OFFSET) % CONFETTI_SEED_MOD / CONFETTI_SEED_MOD.toFloat()
            val speed = CONFETTI_BASE_SPEED + (i % CONFETTI_SPEED_BUCKETS) * CONFETTI_SPEED_STEP
            val yFrac = (progress * speed + seed) % 1f
            val xBase = ((i * CONFETTI_X_MULT + CONFETTI_X_OFFSET) % CONFETTI_X_MOD) / CONFETTI_X_MOD.toFloat() * size.width
            val sway = sin((yFrac * CONFETTI_SWAY_FREQ + i).toDouble()).toFloat() * CONFETTI_SWAY_AMPLITUDE
            val x = (xBase + sway).coerceIn(0f, size.width)
            val y = yFrac * size.height
            val color = colors[i % colors.size]
            drawCircle(
                color = color,
                radius = CONFETTI_BASE_RADIUS + (i % CONFETTI_RADIUS_BUCKETS) * CONFETTI_RADIUS_STEP,
                center = Offset(x, y),
            )
        }
    }
}

/** Displays transient bottom-aligned status banners for deadlocked hints or debug solvability logs. */
@Composable
fun StatusBannerOverlay(
    noMoves: Boolean,
    debugBanner: String?,
    modifier: Modifier = Modifier,
) {
    val banner: Pair<String, Color>? = when {
        noMoves -> "No moves available. Try Undo or start a new deal." to Color.White
        debugBanner != null -> debugBanner to SolitaireColors.HighlightGold
        else -> null
    }
    banner ?: return
    Surface(
        modifier = modifier.padding(16.dp),
        color = SolitaireColors.BannerBg,
        contentColor = banner.second,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = banner.first,
            modifier = Modifier.padding(12.dp),
            fontSize = if (noMoves) TextUnit.Unspecified else 13.sp,
        )
    }
}
