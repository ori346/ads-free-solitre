package com.adfree.solitaire.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adfree.solitaire.config.BoardLayoutConfig
import com.adfree.solitaire.game.Engine
import com.adfree.solitaire.game.GameState
import com.adfree.solitaire.game.Hint
import com.adfree.solitaire.game.Pile
import com.adfree.solitaire.game.Suit
import com.adfree.solitaire.ui.CardView
import com.adfree.solitaire.ui.EmptySlot
import com.adfree.solitaire.ui.theme.SolitaireColors
import kotlin.math.min
import kotlin.math.roundToInt

private const val FOUNDATION_FALLBACK_LABEL = "A"
private const val STOCK_EMPTY_DONE_LABEL = "✓"
private const val STOCK_EMPTY_RECYCLE_LABEL = "↻"
private const val WASTE_WIDTH_CARDS = 2

private val StockBadgeOuterPadding = 3.dp
private val StockBadgeHorizontalPadding = 6.dp
private val StockBadgeVerticalPadding = 1.5.dp
private val StockBadgeFontSize = 12.sp
private val StockBadgeLineHeight = 13.sp

/**
 * Top board row containing the 4 Foundation piles, fanned Waste slot, and Stock deck,
 * automatically mirroring left-to-right order in left-handed mode.
 */
@Composable
fun TopPlayRow(
    state: GameState,
    gestureState: BoardGestureState,
    hint: Hint?,
    activeDragTarget: Pile?,
    cardW: Dp,
    cardH: Dp,
    cardWpx: Float,
    back: Int,
    dark: Boolean,
    leftHanded: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(layout.boardGap)) {
        if (leftHanded) {
            StockSlot(state, gestureState, hint, cardW, cardH, back, dark, fourColor, layout)
            WasteSlot(state, gestureState, hint, cardW, cardH, cardWpx, back, dark, leftHanded, fourColor, layout)
            FoundationsGroup(state, gestureState, hint, activeDragTarget, cardW, cardH, back, dark, fourColor, layout)
        } else {
            FoundationsGroup(state, gestureState, hint, activeDragTarget, cardW, cardH, back, dark, fourColor, layout)
            WasteSlot(state, gestureState, hint, cardW, cardH, cardWpx, back, dark, leftHanded, fourColor, layout)
            StockSlot(state, gestureState, hint, cardW, cardH, back, dark, fourColor, layout)
        }
    }
}

/** Renders the four suit foundation piles. */
@Composable
fun FoundationsGroup(
    state: GameState,
    gestureState: BoardGestureState,
    hint: Hint?,
    activeDragTarget: Pile?,
    cardW: Dp,
    cardH: Dp,
    back: Int,
    dark: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    val density = LocalDensity.current
    Engine.allFoundations.forEachIndexed { idx, foundation ->
        val cards = state.pile(foundation)
        val slotHighlight = (hint?.to == foundation && cards.isEmpty()) ||
            (activeDragTarget == foundation && cards.isEmpty())
        Box(Modifier.trackPile(gestureState, foundation).size(cardW, cardH)) {
            EmptySlot(
                w = cardW,
                h = cardH,
                label = Suit.entries.getOrNull(idx)?.symbol ?: FOUNDATION_FALLBACK_LABEL,
                highlight = slotHighlight,
                layoutConfig = layout,
            )
            SideEffect {
                gestureState.cardOffsets[foundation] = cards.map { Offset.Zero }
            }
            val startIdx = maxOf(0, cards.size - layout.foundationVisibleCards)
            for (i in startIdx until cards.size) {
                val isHidden = gestureState.isCardHidden(foundation, i)
                val isTop = i == cards.lastIndex
                CardView(
                    card = cards[i],
                    w = cardW,
                    h = cardH,
                    back = back,
                    dark = dark,
                    highlight = isTop && gestureState.isCardHighlighted(foundation, i, cards.size, hint, activeDragTarget),
                    fourColor = fourColor,
                    modifier = Modifier
                        .offset { IntOffset(if (isTop) gestureState.shakeOffsetPx(foundation, i, density) else 0, 0) }
                        .alpha(if (isHidden) 0f else 1f)
                        .then(if (isTop) Modifier.cardPointerGestures(gestureState, foundation, i) else Modifier),
                    layoutConfig = layout,
                )
            }
        }
    }
}

/** Renders the fanned waste pile slots with directional alignment for right/left-handed layouts. */
@Composable
fun WasteSlot(
    state: GameState,
    gestureState: BoardGestureState,
    hint: Hint?,
    cardW: Dp,
    cardH: Dp,
    cardWpx: Float,
    back: Int,
    dark: Boolean,
    leftHanded: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    val density = LocalDensity.current
    Box(Modifier.trackPile(gestureState, Pile.Waste).size(cardW * WASTE_WIDTH_CARDS + layout.boardGap, cardH)) {
        val shown = min(layout.wasteMaxRenderedCards, state.waste.size)
        val first = state.waste.size - shown
        val visibleTopCount = min(layout.wasteMaxFannedCards, state.waste.size)
        val firstVisibleTop = state.waste.size - visibleTopCount
        val fan = cardWpx * layout.wasteFanOffsetFraction
        val baseOffset = with(density) { (cardW + layout.boardGap).toPx() }
        val offsets = state.waste.indices.map { i ->
            val k = maxOf(0, i - firstVisibleTop)
            val x = if (leftHanded) {
                (visibleTopCount - 1 - k) * fan
            } else {
                baseOffset - (visibleTopCount - 1 - k) * fan
            }
            Offset(x, 0f)
        }
        SideEffect {
            gestureState.cardOffsets[Pile.Waste] = offsets
        }
        for (i in first until state.waste.size) {
            val isHidden = gestureState.isCardHidden(Pile.Waste, i)
            val isTop = i == state.waste.lastIndex
            val xPx = offsets[i].x.roundToInt() + if (isTop) gestureState.shakeOffsetPx(Pile.Waste, i, density) else 0
            CardView(
                card = state.waste[i],
                w = cardW,
                h = cardH,
                back = back,
                dark = dark,
                highlight = isTop && hint?.from == Pile.Waste,
                fourColor = fourColor,
                modifier = Modifier
                    .offset { IntOffset(xPx, 0) }
                    .alpha(if (isHidden) 0f else 1f)
                    .then(
                        if (state.waste.isNotEmpty()) {
                            Modifier.cardPointerGestures(gestureState, Pile.Waste, state.waste.lastIndex)
                        } else {
                            Modifier
                        },
                    ),
                layoutConfig = layout,
            )
        }
    }
}

/** Renders the stock deck with remaining card count badge and tap-to-draw handler. */
@Composable
fun StockSlot(
    state: GameState,
    gestureState: BoardGestureState,
    hint: Hint?,
    cardW: Dp,
    cardH: Dp,
    back: Int,
    dark: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    Box(
        Modifier
            .trackPile(gestureState, Pile.Stock)
            .size(cardW, cardH)
            .pointerInput(Unit) {
                detectTapGestures { gestureState.onStockTap() }
            },
    ) {
        if (state.stock.isEmpty()) {
            EmptySlot(
                w = cardW,
                h = cardH,
                label = if (state.waste.isEmpty()) STOCK_EMPTY_DONE_LABEL else STOCK_EMPTY_RECYCLE_LABEL,
                highlight = hint?.from == Pile.Stock,
                layoutConfig = layout,
            )
        } else {
            CardView(
                card = state.stock.last(),
                w = cardW,
                h = cardH,
                back = back,
                dark = dark,
                highlight = hint?.from == Pile.Stock,
                fourColor = fourColor,
                layoutConfig = layout,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(StockBadgeOuterPadding)
                    .clip(CircleShape)
                    .background(SolitaireColors.StockCountBadgeBg)
                    .padding(horizontal = StockBadgeHorizontalPadding, vertical = StockBadgeVerticalPadding),
            ) {
                Text(
                    text = state.stock.size.toString(),
                    color = Color.White,
                    fontSize = StockBadgeFontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = StockBadgeLineHeight,
                )
            }
        }
    }
}
