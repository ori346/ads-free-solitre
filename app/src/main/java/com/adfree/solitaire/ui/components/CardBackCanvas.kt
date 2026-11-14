package com.adfree.solitaire.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.adfree.solitaire.ui.theme.CardBacks
import com.adfree.solitaire.ui.theme.SolitaireColors

private const val CARD_BACK_MARGIN_FRACTION = 0.075f
private const val CARD_BACK_INNER_RADIUS_FRACTION = 0.045f
private const val CARD_BACK_FILIGREE_INSET_FRACTION = 0.035f
private const val CARD_BACK_LATTICE_STEP_FRACTION = 0.16f
private const val CARD_BACK_LATTICE_STROKE_FRACTION = 0.028f
private const val CARD_BACK_LATTICE_ALPHA = 0.55f
private const val CARD_BACK_MEDALLION_W_FRACTION = 0.28f
private const val CARD_BACK_MEDALLION_H_FRACTION = 0.22f
private const val CARD_BACK_MEDALLION_FILL_ALPHA = 0.22f
private const val CARD_BACK_MEDALLION_STROKE_ALPHA = 0.7f
private const val CARD_BACK_MEDALLION_STROKE_FRACTION = 0.025f
private const val CARD_BACK_FRAME_ALPHA = 0.85f
private const val CARD_BACK_FRAME_RADIUS_FRACTION = 0.025f
private const val CARD_BACK_FRAME_STROKE_FRACTION = 0.022f

/** Draws a classic diamond-lattice filigree card back corresponding to [index]. */
@Composable
fun CardBack(index: Int) {
    val (base, accent) = CardBacks.getOrElse(index) { CardBacks[0] }
    Canvas(Modifier.fillMaxSize().background(SolitaireColors.CardBackFrameBg)) {
        val w = size.width
        val h = size.height
        val margin = w * CARD_BACK_MARGIN_FRACTION
        val innerW = w - 2 * margin
        val innerH = h - 2 * margin
        val radius = CornerRadius(w * CARD_BACK_INNER_RADIUS_FRACTION, w * CARD_BACK_INNER_RADIUS_FRACTION)

        drawRoundRect(
            color = base,
            topLeft = Offset(margin, margin),
            size = Size(innerW, innerH),
            cornerRadius = radius,
        )

        val pad = margin + w * CARD_BACK_FILIGREE_INSET_FRACTION
        val patRight = w - pad
        val patBottom = h - pad
        clipRect(left = pad, top = pad, right = patRight, bottom = patBottom) {
            val step = w * CARD_BACK_LATTICE_STEP_FRACTION
            val stroke = w * CARD_BACK_LATTICE_STROKE_FRACTION
            val latticeColor = accent.copy(alpha = CARD_BACK_LATTICE_ALPHA)
            var x = pad - innerH
            while (x < patRight + innerH) {
                drawLine(color = latticeColor, start = Offset(x, pad), end = Offset(x + innerH, patBottom), strokeWidth = stroke)
                drawLine(color = latticeColor, start = Offset(x + innerH, pad), end = Offset(x, patBottom), strokeWidth = stroke)
                x += step
            }

            val cx = w / 2f
            val cy = h / 2f
            val dw = innerW * CARD_BACK_MEDALLION_W_FRACTION
            val dh = innerH * CARD_BACK_MEDALLION_H_FRACTION
            val diamond = Path().apply {
                moveTo(cx, cy - dh)
                lineTo(cx + dw, cy)
                lineTo(cx, cy + dh)
                lineTo(cx - dw, cy)
                close()
            }
            drawPath(diamond, color = Color.White.copy(alpha = CARD_BACK_MEDALLION_FILL_ALPHA))
            drawPath(
                diamond,
                color = Color.White.copy(alpha = CARD_BACK_MEDALLION_STROKE_ALPHA),
                style = Stroke(width = w * CARD_BACK_MEDALLION_STROKE_FRACTION),
            )
        }

        val frameRadius = CornerRadius(w * CARD_BACK_FRAME_RADIUS_FRACTION, w * CARD_BACK_FRAME_RADIUS_FRACTION)
        drawRoundRect(
            color = Color.White.copy(alpha = CARD_BACK_FRAME_ALPHA),
            topLeft = Offset(pad, pad),
            size = Size(w - 2 * pad, h - 2 * pad),
            cornerRadius = frameRadius,
            style = Stroke(width = w * CARD_BACK_FRAME_STROKE_FRACTION),
        )
    }
}
