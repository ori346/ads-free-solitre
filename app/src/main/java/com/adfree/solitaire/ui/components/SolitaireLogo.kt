package com.adfree.solitaire.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import com.adfree.solitaire.ui.theme.SolitaireColors

private const val LOGO_DESIGN_SIZE = 108f
private const val OUTER_BADGE_RADIUS_FRACTION = 0.48f
private const val INNER_BADGE_RADIUS_FRACTION = 0.42f
private const val GOLD_RIM_RADIUS_FRACTION = 0.44f
private const val GOLD_RIM_STROKE = 2f
private const val FAN_PIVOT_X = 54f
private const val FAN_PIVOT_Y = 58f

private const val BACK_CARD_TILT_DEG = -12f
private const val BACK_CARD_LEFT = 26f
private const val BACK_CARD_TOP = 23f
private const val BACK_CARD_WIDTH = 38f
private const val BACK_CARD_HEIGHT = 52f
private const val BACK_CARD_CORNER = 5f
private const val BACK_CARD_BORDER_STROKE = 1f

private const val FRONT_CARD_TILT_DEG = 8f
private const val FRONT_CARD_SHADOW_LEFT = 39f
private const val FRONT_CARD_SHADOW_TOP = 25f
private const val FRONT_CARD_LEFT = 38f
private const val FRONT_CARD_TOP = 23f
private const val FRONT_CARD_WIDTH = 40f
private const val FRONT_CARD_HEIGHT = 54f
private const val FRONT_CARD_CORNER = 5.5f
private const val FRONT_CARD_BORDER_STROKE = 1.8f

/**
 * In-app emblem matching the adaptive launcher icon (fanned Heart + Ace of Spades with gold trim).
 */
@Composable
fun SolitaireLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = w / LOGO_DESIGN_SIZE
        val center = Offset(w / 2f, h / 2f)
        val fanPivot = Offset(FAN_PIVOT_X * s, FAN_PIVOT_Y * s)

        // Outer emerald badge circle with gold rim
        drawCircle(color = SolitaireColors.LogoOuterEmerald, radius = w * OUTER_BADGE_RADIUS_FRACTION, center = center)
        drawCircle(color = SolitaireColors.LogoInnerEmerald, radius = w * INNER_BADGE_RADIUS_FRACTION, center = center)
        drawCircle(
            color = SolitaireColors.LogoGoldRim,
            radius = w * GOLD_RIM_RADIUS_FRACTION,
            center = center,
            style = Stroke(width = GOLD_RIM_STROKE * s),
        )

        // Left fanned card (Heart)
        withTransform({ rotate(degrees = BACK_CARD_TILT_DEG, pivot = fanPivot) }) {
            val topLeft = Offset(BACK_CARD_LEFT * s, BACK_CARD_TOP * s)
            val cardSize = Size(BACK_CARD_WIDTH * s, BACK_CARD_HEIGHT * s)
            val corner = CornerRadius(BACK_CARD_CORNER * s, BACK_CARD_CORNER * s)
            drawRoundRect(
                color = SolitaireColors.LogoBackCardFill,
                topLeft = topLeft,
                size = cardSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = SolitaireColors.LogoBackCardBorder,
                topLeft = topLeft,
                size = cardSize,
                cornerRadius = corner,
                style = Stroke(width = BACK_CARD_BORDER_STROKE * s),
            )
            drawPath(buildLogoHeartPath(s), color = SolitaireColors.LogoHeartRed)
        }

        // Right/front fanned card (Ace of Spades)
        withTransform({ rotate(degrees = FRONT_CARD_TILT_DEG, pivot = fanPivot) }) {
            val cardSize = Size(FRONT_CARD_WIDTH * s, FRONT_CARD_HEIGHT * s)
            val corner = CornerRadius(FRONT_CARD_CORNER * s, FRONT_CARD_CORNER * s)
            drawRoundRect(
                color = SolitaireColors.LogoFrontCardShadow,
                topLeft = Offset(FRONT_CARD_SHADOW_LEFT * s, FRONT_CARD_SHADOW_TOP * s),
                size = cardSize,
                cornerRadius = corner,
            )
            val frontTopLeft = Offset(FRONT_CARD_LEFT * s, FRONT_CARD_TOP * s)
            drawRoundRect(
                color = SolitaireColors.CardFaceLight,
                topLeft = frontTopLeft,
                size = cardSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = SolitaireColors.HighlightGold,
                topLeft = frontTopLeft,
                size = cardSize,
                cornerRadius = corner,
                style = Stroke(width = FRONT_CARD_BORDER_STROKE * s),
            )
            drawPath(buildLogoSpadePath(s), color = SolitaireColors.LogoSpadeBlack)
        }
    }
}

private fun buildLogoHeartPath(s: Float): Path = Path().apply {
    moveTo(45f * s, 58f * s)
    cubicTo(45f * s, 58f * s, 35f * s, 50f * s, 35f * s, 44f * s)
    cubicTo(35f * s, 40.5f * s, 37.8f * s, 38f * s, 40.8f * s, 38f * s)
    cubicTo(42.8f * s, 38f * s, 44.2f * s, 39.2f * s, 45f * s, 40.6f * s)
    cubicTo(45.8f * s, 39.2f * s, 47.2f * s, 38f * s, 49.2f * s, 38f * s)
    cubicTo(52.2f * s, 38f * s, 55f * s, 40.5f * s, 55f * s, 44f * s)
    cubicTo(55f * s, 50f * s, 45f * s, 58f * s, 45f * s, 58f * s)
    close()
}

private fun buildLogoSpadePath(s: Float): Path = Path().apply {
    moveTo(58f * s, 37f * s)
    cubicTo(58f * s, 37f * s, 47f * s, 47f * s, 47f * s, 53f * s)
    cubicTo(47f * s, 56.4f * s, 49.6f * s, 58.8f * s, 52.8f * s, 58.8f * s)
    cubicTo(54.7f * s, 58.8f * s, 56.1f * s, 57.9f * s, 57f * s, 56.6f * s)
    lineTo(55f * s, 63f * s)
    lineTo(61f * s, 63f * s)
    lineTo(59f * s, 56.6f * s)
    cubicTo(59.9f * s, 57.9f * s, 61.3f * s, 58.8f * s, 63.2f * s, 58.8f * s)
    cubicTo(66.4f * s, 58.8f * s, 69f * s, 56.4f * s, 69f * s, 53f * s)
    cubicTo(69f * s, 47f * s, 58f * s, 37f * s, 58f * s, 37f * s)
    close()
}
