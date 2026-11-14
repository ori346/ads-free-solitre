package com.adfree.solitaire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.config.BoardLayoutConfig
import com.adfree.solitaire.config.DefaultUiConfig
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.game.Suit
import com.adfree.solitaire.ui.components.CardBack
import com.adfree.solitaire.ui.components.drawRoyalPortrait
import com.adfree.solitaire.ui.components.suitColor
import com.adfree.solitaire.ui.components.suitPath
import com.adfree.solitaire.ui.theme.SolitaireColors

private const val CARD_DESIGN_WIDTH = 160f
private const val CARD_DESIGN_HEIGHT = 232f
private const val RANK_TEN_SIZE_FRACTION = 0.34f
private const val RANK_DEFAULT_SIZE_FRACTION = 0.37f
private const val RANK_TEN_LETTER_SPACING_FRACTION = -0.036f
private const val RANK_BOX_START_FRACTION = 0.055f
private const val RANK_BOX_TOP_FRACTION = 0.015f
private const val RANK_BOX_WIDTH_FRACTION = 0.54f
private const val RANK_BOX_HEIGHT_FRACTION = 0.38f

private const val HEADER_PIP_X = 123.5f
private const val HEADER_PIP_Y = 32.5f
private const val HEADER_PIP_SCALE = -2.85f
private const val CENTER_PIP_X = 80f
private const val CENTER_PIP_Y = 148f
private const val CENTER_PIP_SCALE = -5.85f
private const val EMPTY_SLOT_PIP_X = 80f
private const val EMPTY_SLOT_PIP_Y = 116f
private const val EMPTY_SLOT_PIP_SCALE = -3.8f
private const val EMPTY_SLOT_LABEL_SIZE_FRACTION = 0.38f

private val CardElevation = 2.5.dp
private val HighlightBorderWidth = 2.8.dp
private val NormalBorderWidth = 1.dp

private val CompactRankTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Renders a playing card (face-up or face-down) at explicit pixel-aligned dimensions. */
@Composable
fun CardView(
    card: Card,
    w: Dp,
    h: Dp,
    back: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    fourColor: Boolean = false,
    layoutConfig: BoardLayoutConfig = DefaultUiConfig.layout,
) {
    val shape = RoundedCornerShape(w * layoutConfig.cardCornerRadiusFraction)
    val border = if (highlight) {
        Modifier.border(HighlightBorderWidth, SolitaireColors.HighlightGold, shape)
    } else {
        Modifier.border(
            NormalBorderWidth,
            if (dark) SolitaireColors.CardBorderDark else SolitaireColors.CardBorderLight,
            shape,
        )
    }
    Box(
        modifier
            .size(w, h)
            .shadow(elevation = CardElevation, shape = shape, clip = false)
            .clip(shape)
            .then(border),
    ) {
        if (card.faceUp) {
            CardFace(card, w, dark, fourColor = fourColor)
        } else {
            CardBack(back)
        }
    }
}

/**
 * Comfortable, eye-friendly Solitaire card face with a top rank/suit strip for stacked readability
 * and either a centered suit emblem (A..10) or a framed royal portrait (J, Q, K).
 */
@Composable
fun CardFace(card: Card, w: Dp, dark: Boolean, fourColor: Boolean) {
    val color = suitColor(card.suit, fourColor)
    val bg = if (dark) SolitaireColors.CardFaceDark else SolitaireColors.CardFaceLight
    val density = LocalDensity.current
    val isTen = card.rank == Card.RANK_TEN
    val rankFontSize = with(density) { (w * if (isTen) RANK_TEN_SIZE_FRACTION else RANK_DEFAULT_SIZE_FRACTION).toSp() }
    val rankLetterSpacing = if (isTen) with(density) { (w * RANK_TEN_LETTER_SPACING_FRACTION).toSp() } else 0.sp

    Box(Modifier.fillMaxSize().background(bg)) {
        val path = remember(card.suit) { suitPath(card.suit) }

        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / CARD_DESIGN_WIDTH
            val sy = size.height / CARD_DESIGN_HEIGHT

            withTransform({
                translate(left = HEADER_PIP_X * sx, top = HEADER_PIP_Y * sy)
                scale(scaleX = HEADER_PIP_SCALE * sx, scaleY = HEADER_PIP_SCALE * sy, pivot = Offset.Zero)
            }) {
                drawPath(path, color = color)
            }

            if (card.isRoyalty) {
                drawRoyalPortrait(
                    rank = card.rank,
                    suitPath = path,
                    suitColor = color,
                    sx = sx,
                    sy = sy,
                )
            } else {
                withTransform({
                    translate(left = CENTER_PIP_X * sx, top = CENTER_PIP_Y * sy)
                    scale(scaleX = CENTER_PIP_SCALE * sx, scaleY = CENTER_PIP_SCALE * sy, pivot = Offset.Zero)
                }) {
                    drawPath(path, color = color)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = w * RANK_BOX_START_FRACTION, top = w * RANK_BOX_TOP_FRACTION)
                .size(width = w * RANK_BOX_WIDTH_FRACTION, height = w * RANK_BOX_HEIGHT_FRACTION),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = card.rankLabel,
                color = color,
                fontSize = rankFontSize,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                letterSpacing = rankLetterSpacing,
                lineHeight = rankFontSize,
                softWrap = false,
                style = CompactRankTextStyle,
            )
        }
    }
}

/** Empty pile placeholder displaying either a suit watermark or a short action symbol. */
@Composable
fun EmptySlot(
    w: Dp,
    h: Dp,
    label: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    layoutConfig: BoardLayoutConfig = DefaultUiConfig.layout,
) {
    val shape = RoundedCornerShape(w * layoutConfig.cardCornerRadiusFraction)
    val density = LocalDensity.current
    val suitForLabel = remember(label) {
        Suit.entries.firstOrNull { it.symbol == label }
    }
    Box(
        modifier
            .size(w, h)
            .clip(shape)
            .background(if (highlight) SolitaireColors.HighlightFill else SolitaireColors.EmptySlotFill)
            .border(
                width = if (highlight) HighlightBorderWidth else NormalBorderWidth,
                color = if (highlight) SolitaireColors.HighlightGold else SolitaireColors.EmptySlotBorder,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (suitForLabel != null) {
            val path = remember(suitForLabel) { suitPath(suitForLabel) }
            val tint = if (highlight) SolitaireColors.HighlightGold else SolitaireColors.EmptySlotSuitTint
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / CARD_DESIGN_WIDTH
                val sy = size.height / CARD_DESIGN_HEIGHT
                withTransform({
                    translate(left = EMPTY_SLOT_PIP_X * sx, top = EMPTY_SLOT_PIP_Y * sy)
                    scale(scaleX = EMPTY_SLOT_PIP_SCALE * sx, scaleY = EMPTY_SLOT_PIP_SCALE * sy, pivot = Offset.Zero)
                }) {
                    drawPath(path, color = tint)
                }
            }
        } else {
            Text(
                text = label,
                color = if (highlight) SolitaireColors.HighlightGold else SolitaireColors.EmptySlotLabelTint,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { (w * EMPTY_SLOT_LABEL_SIZE_FRACTION).toSp() },
            )
        }
    }
}
