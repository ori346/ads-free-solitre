package com.adfree.solitaire.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.ui.theme.SolitaireColors

private const val FRAME_LEFT = 14f
private const val FRAME_TOP = 73f
private const val FRAME_RIGHT = 146f
private const val FRAME_BOTTOM = 217f
private const val FRAME_CORNER = 5f
private const val INNER_FRAME_INSET = 3f
private const val INNER_FRAME_CORNER = 3.5f
private const val HALO_CENTER_X = 80f
private const val HALO_CENTER_Y = 134f
private const val HALO_RADIUS = 52f
private const val WATERMARK_X = 127f
private const val WATERMARK_Y = 93f
private const val WATERMARK_SCALE = -1.25f
private const val WATERMARK_ALPHA = 0.85f
private const val MANTLE_STRIPE_ROWS = 4
private const val MANTLE_STRIPE_BASE_Y = 172f
private const val MANTLE_STRIPE_STEP_Y = 11f

/**
 * Draws a classic illustrated playing-card court portrait (Jack, Queen, King) inside the lower body
 * (y = 73..216 in 160x232 card space, strictly below the top header strip so stacked cards stay readable).
 */
fun DrawScope.drawRoyalPortrait(
    rank: Int,
    suitPath: Path,
    suitColor: Color,
    sx: Float,
    sy: Float,
) {
    val left = FRAME_LEFT * sx
    val top = FRAME_TOP * sy
    val right = FRAME_RIGHT * sx
    val bottom = FRAME_BOTTOM * sy
    val frameW = right - left
    val frameH = bottom - top

    drawRoundRect(
        color = SolitaireColors.RoyalCanvasBg,
        topLeft = Offset(left, top),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(FRAME_CORNER * sx, FRAME_CORNER * sy),
    )

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        drawCircle(
            color = SolitaireColors.RoyalHalo,
            radius = HALO_RADIUS * sx,
            center = Offset(HALO_CENTER_X * sx, HALO_CENTER_Y * sy),
        )

        withTransform({
            translate(left = WATERMARK_X * sx, top = WATERMARK_Y * sy)
            scale(scaleX = WATERMARK_SCALE * sx, scaleY = WATERMARK_SCALE * sy, pivot = Offset.Zero)
        }) {
            drawPath(suitPath, color = suitColor.copy(alpha = WATERMARK_ALPHA))
        }

        drawRoyalRobeAndMantle(sx, sy)
        drawRoyalFace(rank, sx, sy)
        drawRoyalCrown(rank, sx, sy)
    }

    drawRoundRect(
        color = SolitaireColors.RoyalDarkInk.copy(alpha = 0.8f),
        topLeft = Offset(left, top),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(FRAME_CORNER * sx, FRAME_CORNER * sy),
        style = Stroke(width = 1.6f * sx),
    )
    val inset = INNER_FRAME_INSET * sx
    drawRoundRect(
        color = SolitaireColors.RoyalGoldDark.copy(alpha = 0.7f),
        topLeft = Offset(left + inset, top + inset),
        size = Size(frameW - 2 * inset, frameH - 2 * inset),
        cornerRadius = CornerRadius(INNER_FRAME_CORNER * sx, INNER_FRAME_CORNER * sy),
        style = Stroke(width = 1.0f * sx),
    )
}

private fun DrawScope.drawRoyalRobeAndMantle(sx: Float, sy: Float) {
    val robePath = Path().apply {
        moveTo(14f * sx, 217f * sy)
        quadraticTo(20f * sx, 160f * sy, 52f * sx, 154f * sy)
        lineTo(108f * sx, 154f * sy)
        quadraticTo(140f * sx, 160f * sy, 146f * sx, 217f * sy)
        close()
    }
    drawPath(robePath, color = SolitaireColors.RoyalCrimson)
    drawPath(robePath, color = SolitaireColors.RoyalDarkInk, style = Stroke(width = 1.6f * sx))

    val leftMantle = Path().apply {
        moveTo(14f * sx, 217f * sy)
        quadraticTo(22f * sx, 168f * sy, 48f * sx, 156f * sy)
        lineTo(66f * sx, 217f * sy)
        close()
    }
    val rightMantle = Path().apply {
        moveTo(146f * sx, 217f * sy)
        quadraticTo(138f * sx, 168f * sy, 112f * sx, 156f * sy)
        lineTo(94f * sx, 217f * sy)
        close()
    }
    drawPath(leftMantle, color = SolitaireColors.RoyalBlue)
    drawPath(rightMantle, color = SolitaireColors.RoyalBlue)

    val collarPath = Path().apply {
        moveTo(42f * sx, 155f * sy)
        lineTo(80f * sx, 198f * sy)
        lineTo(118f * sx, 155f * sy)
        lineTo(102f * sx, 153f * sy)
        lineTo(80f * sx, 179f * sy)
        lineTo(58f * sx, 153f * sy)
        close()
    }
    drawPath(collarPath, color = SolitaireColors.RoyalGold)
    drawPath(collarPath, color = SolitaireColors.RoyalGoldDark, style = Stroke(width = 1.4f * sx))

    val jewelDots = listOf(
        Offset(53f * sx, 160f * sy),
        Offset(65f * sx, 173f * sy),
        Offset(80f * sx, 187f * sy),
        Offset(95f * sx, 173f * sy),
        Offset(107f * sx, 160f * sy),
    )
    jewelDots.forEach { pt ->
        drawCircle(color = SolitaireColors.RoyalCrimson, radius = 2.6f * sx, center = pt)
    }

    for (row in 0 until MANTLE_STRIPE_ROWS) {
        val yStripe = (MANTLE_STRIPE_BASE_Y + row * MANTLE_STRIPE_STEP_Y) * sy
        drawLine(
            color = SolitaireColors.RoyalGold,
            start = Offset(22f * sx, yStripe),
            end = Offset(52f * sx, yStripe + 5f * sy),
            strokeWidth = 2.5f * sx,
        )
        drawLine(
            color = SolitaireColors.RoyalGold,
            start = Offset(138f * sx, yStripe),
            end = Offset(108f * sx, yStripe + 5f * sy),
            strokeWidth = 2.5f * sx,
        )
    }
}

private fun DrawScope.drawRoyalFace(rank: Int, sx: Float, sy: Float) {
    if (rank == Card.RANK_QUEEN) {
        val veilPath = Path().apply {
            moveTo(44f * sx, 104f * sy)
            quadraticTo(32f * sx, 134f * sy, 38f * sx, 162f * sy)
            lineTo(122f * sx, 162f * sy)
            quadraticTo(128f * sx, 134f * sy, 116f * sx, 104f * sy)
            close()
        }
        drawPath(veilPath, color = SolitaireColors.RoyalVeil)
        drawPath(veilPath, color = SolitaireColors.RoyalGoldDark, style = Stroke(width = 1.3f * sx))
    }

    val leftHair = Path().apply {
        moveTo(54f * sx, 105f * sy)
        cubicTo(38f * sx, 110f * sy, 40f * sx, 146f * sy, 54f * sx, 152f * sy)
        cubicTo(60f * sx, 148f * sy, 58f * sx, 118f * sy, 54f * sx, 105f * sy)
        close()
    }
    val rightHair = Path().apply {
        moveTo(106f * sx, 105f * sy)
        cubicTo(122f * sx, 110f * sy, 120f * sx, 146f * sy, 106f * sx, 152f * sy)
        cubicTo(100f * sx, 148f * sy, 102f * sx, 118f * sy, 106f * sx, 105f * sy)
        close()
    }
    listOf(leftHair, rightHair).forEach { hair ->
        drawPath(hair, color = SolitaireColors.RoyalHairGold)
        drawPath(hair, color = SolitaireColors.RoyalSkinOutline, style = Stroke(width = 1.2f * sx))
    }

    drawRoundRect(
        color = SolitaireColors.RoyalSkin,
        topLeft = Offset(69f * sx, 144f * sy),
        size = Size(22f * sx, 16f * sy),
        cornerRadius = CornerRadius(4f * sx, 4f * sy),
    )

    val facePath = Path().apply { addOval(Rect(55f * sx, 102f * sy, 105f * sx, 152f * sy)) }
    drawPath(facePath, color = SolitaireColors.RoyalSkin)
    drawPath(facePath, color = SolitaireColors.RoyalSkinOutline, style = Stroke(width = 1.4f * sx))

    val fringePath = Path().apply {
        moveTo(55f * sx, 114f * sy)
        quadraticTo(68f * sx, 105f * sy, 80f * sx, 111f * sy)
        quadraticTo(92f * sx, 105f * sy, 105f * sx, 114f * sy)
        lineTo(104f * sx, 104f * sy)
        lineTo(56f * sx, 104f * sy)
        close()
    }
    drawPath(fringePath, color = SolitaireColors.RoyalHairGold)

    drawLine(
        color = SolitaireColors.RoyalSkinOutline,
        start = Offset(64f * sx, 119f * sy),
        end = Offset(74f * sx, 118f * sy),
        strokeWidth = 1.6f * sx,
    )
    drawLine(
        color = SolitaireColors.RoyalSkinOutline,
        start = Offset(86f * sx, 118f * sy),
        end = Offset(96f * sx, 119f * sy),
        strokeWidth = 1.6f * sx,
    )
    drawCircle(color = Color.White, radius = 3.6f * sx, center = Offset(69f * sx, 124f * sy))
    drawCircle(color = Color.White, radius = 3.6f * sx, center = Offset(91f * sx, 124f * sy))
    drawCircle(color = SolitaireColors.RoyalNavy, radius = 2.0f * sx, center = Offset(68.5f * sx, 124f * sy))
    drawCircle(color = SolitaireColors.RoyalNavy, radius = 2.0f * sx, center = Offset(90.5f * sx, 124f * sy))

    val nosePath = Path().apply {
        moveTo(79.5f * sx, 122f * sy)
        lineTo(76.5f * sx, 133f * sy)
        lineTo(81.5f * sx, 134f * sy)
    }
    drawPath(nosePath, color = SolitaireColors.RoyalSkinOutline, style = Stroke(width = 1.4f * sx))

    if (rank == Card.RANK_KING) {
        val beardPath = Path().apply {
            moveTo(57f * sx, 132f * sy)
            quadraticTo(58f * sx, 154f * sy, 80f * sx, 155f * sy)
            quadraticTo(102f * sx, 154f * sy, 103f * sx, 132f * sy)
            quadraticTo(92f * sx, 142f * sy, 80f * sx, 142f * sy)
            quadraticTo(68f * sx, 142f * sy, 57f * sx, 132f * sy)
            close()
        }
        drawPath(beardPath, color = SolitaireColors.RoyalHairGold)
        drawPath(beardPath, color = SolitaireColors.RoyalSkinOutline, style = Stroke(width = 1.3f * sx))

        val mustache = Path().apply {
            moveTo(80f * sx, 137f * sy)
            quadraticTo(71f * sx, 135f * sy, 66f * sx, 140f * sy)
            moveTo(80f * sx, 137f * sy)
            quadraticTo(89f * sx, 135f * sy, 94f * sx, 140f * sy)
        }
        drawPath(mustache, color = SolitaireColors.RoyalSkinOutline, style = Stroke(width = 1.8f * sx))
    } else {
        val mouthPath = Path().apply {
            moveTo(74f * sx, 140f * sy)
            quadraticTo(80f * sx, 143.5f * sy, 86f * sx, 140f * sy)
        }
        drawPath(
            mouthPath,
            color = if (rank == Card.RANK_QUEEN) SolitaireColors.RoyalCrimson else SolitaireColors.RoyalSkinOutline,
            style = Stroke(width = 2.0f * sx),
        )
    }
}

private fun DrawScope.drawRoyalCrown(rank: Int, sx: Float, sy: Float) {
    when (rank) {
        Card.RANK_KING -> {
            val crown = Path().apply {
                moveTo(50f * sx, 107f * sy)
                lineTo(45f * sx, 84f * sy)
                lineTo(62f * sx, 95f * sy)
                lineTo(71f * sx, 81f * sy)
                lineTo(80f * sx, 93f * sy)
                lineTo(89f * sx, 81f * sy)
                lineTo(98f * sx, 95f * sy)
                lineTo(115f * sx, 84f * sy)
                lineTo(110f * sx, 107f * sy)
                close()
            }
            drawPath(crown, color = SolitaireColors.RoyalGold)
            drawPath(crown, color = SolitaireColors.RoyalGoldDark, style = Stroke(width = 1.5f * sx))
            drawRect(
                color = SolitaireColors.RoyalCrimson,
                topLeft = Offset(51f * sx, 101f * sy),
                size = Size(58f * sx, 5f * sy),
            )
            drawCircle(color = SolitaireColors.RoyalCrimson, radius = 2.6f * sx, center = Offset(80f * sx, 96f * sy))
            drawCircle(color = SolitaireColors.RoyalBlue, radius = 2.3f * sx, center = Offset(63f * sx, 97f * sy))
            drawCircle(color = SolitaireColors.RoyalBlue, radius = 2.3f * sx, center = Offset(97f * sx, 97f * sy))
        }
        Card.RANK_QUEEN -> {
            val tiara = Path().apply {
                moveTo(52f * sx, 107f * sy)
                lineTo(48f * sx, 88f * sy)
                quadraticTo(64f * sx, 98f * sy, 80f * sx, 80f * sy)
                quadraticTo(96f * sx, 98f * sy, 112f * sx, 88f * sy)
                lineTo(108f * sx, 107f * sy)
                close()
            }
            drawPath(tiara, color = SolitaireColors.RoyalGold)
            drawPath(tiara, color = SolitaireColors.RoyalGoldDark, style = Stroke(width = 1.5f * sx))
            drawRect(
                color = SolitaireColors.RoyalBlue,
                topLeft = Offset(53f * sx, 102f * sy),
                size = Size(54f * sx, 4.5f * sy),
            )
            drawCircle(color = SolitaireColors.RoyalCrimson, radius = 3.0f * sx, center = Offset(80f * sx, 94f * sy))
            drawCircle(color = Color.White, radius = 2.0f * sx, center = Offset(64f * sx, 97f * sy))
            drawCircle(color = Color.White, radius = 2.0f * sx, center = Offset(96f * sx, 97f * sy))
        }
        else -> {
            val cap = Path().apply {
                moveTo(48f * sx, 107f * sy)
                cubicTo(42f * sx, 90f * sy, 62f * sx, 81f * sy, 84f * sx, 83f * sy)
                cubicTo(104f * sx, 84f * sy, 118f * sx, 92f * sy, 110f * sx, 107f * sy)
                close()
            }
            drawPath(cap, color = SolitaireColors.RoyalCrimson)
            drawPath(cap, color = SolitaireColors.RoyalDarkInk, style = Stroke(width = 1.4f * sx))
            val brim = Path().apply {
                moveTo(50f * sx, 108f * sy)
                lineTo(48f * sx, 98f * sy)
                lineTo(66f * sx, 101f * sy)
                lineTo(80f * sx, 94f * sy)
                lineTo(94f * sx, 101f * sy)
                lineTo(112f * sx, 98f * sy)
                lineTo(110f * sx, 108f * sy)
                close()
            }
            drawPath(brim, color = SolitaireColors.RoyalGold)
            drawPath(brim, color = SolitaireColors.RoyalGoldDark, style = Stroke(width = 1.3f * sx))
        }
    }
}
