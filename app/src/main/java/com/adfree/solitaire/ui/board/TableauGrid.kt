package com.adfree.solitaire.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.adfree.solitaire.config.BoardLayoutConfig
import com.adfree.solitaire.game.Engine
import com.adfree.solitaire.game.GameState
import com.adfree.solitaire.game.Hint
import com.adfree.solitaire.game.Pile
import com.adfree.solitaire.ui.CardView
import com.adfree.solitaire.ui.EmptySlot
import kotlin.math.roundToInt

private const val TABLEAU_EMPTY_LABEL = "K"

/** Renders the 7 Solitaire tableau columns with adaptive vertical compression for deep stacks. */
@Composable
fun TableauGrid(
    state: GameState,
    gestureState: BoardGestureState,
    hint: Hint?,
    activeDragTarget: Pile?,
    cardW: Dp,
    cardH: Dp,
    cardHpx: Float,
    back: Int,
    dark: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    val density = LocalDensity.current
    Row(
        Modifier.padding(top = layout.tableauTopPadding),
        horizontalArrangement = Arrangement.spacedBy(layout.boardGap),
    ) {
        Engine.allTableaus.forEach { tableau ->
            val col = state.pile(tableau)
            BoxWithConstraints(Modifier.trackPile(gestureState, tableau).width(cardW).fillMaxHeight()) {
                val availPx = with(density) { (maxHeight - cardH).toPx() }.coerceAtLeast(0f)
                var downStep = cardHpx * layout.tableauFaceDownStepFraction
                var upStep = cardHpx * layout.tableauFaceUpStepFraction
                val totalHeight = col.dropLast(1).sumOf { (if (it.faceUp) upStep else downStep).toDouble() }.toFloat()
                if (totalHeight > availPx && totalHeight > 0f) {
                    val scale = (availPx / totalHeight).coerceAtLeast(0f)
                    downStep *= scale
                    upStep *= scale
                }
                var y = 0f
                val ys = col.map { card -> y.also { y += if (card.faceUp) upStep else downStep } }
                SideEffect {
                    gestureState.cardOffsets[tableau] = ys.map { Offset(0f, it) }
                }

                val emptyHighlight = (hint?.to == tableau || activeDragTarget == tableau) && col.isEmpty()
                if (col.isEmpty()) {
                    EmptySlot(cardW, cardH, TABLEAU_EMPTY_LABEL, emptyHighlight, layoutConfig = layout)
                }

                // Route taps/drags on face-down cards to the first face-up card for responsive stack interaction.
                val firstFaceUpIdx = col.indexOfFirst { it.faceUp }
                col.forEachIndexed { j, card ->
                    val isHidden = gestureState.isCardHidden(tableau, j)
                    val targetIdx = if (card.faceUp) j else firstFaceUpIdx
                    CardView(
                        card = card,
                        w = cardW,
                        h = cardH,
                        back = back,
                        dark = dark,
                        highlight = gestureState.isCardHighlighted(tableau, j, col.size, hint, activeDragTarget),
                        fourColor = fourColor,
                        modifier = Modifier
                            .offset { IntOffset(gestureState.shakeOffsetPx(tableau, j, density), ys[j].roundToInt()) }
                            .alpha(if (isHidden) 0f else 1f)
                            .then(
                                if (targetIdx >= 0) {
                                    Modifier.cardPointerGestures(gestureState, tableau, targetIdx)
                                } else {
                                    Modifier
                                },
                            ),
                        layoutConfig = layout,
                    )
                }
            }
        }
    }
}

/** Renders cards currently gliding smoothly between piles via [BoardGestureState.flight]. */
@Composable
fun FlightOverlay(
    gestureState: BoardGestureState,
    cardW: Dp,
    cardH: Dp,
    back: Int,
    dark: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    val fl = gestureState.flight ?: return
    val destBounds = gestureState.pileBounds[fl.destPile]
    val destOffs = gestureState.cardOffsets[fl.destPile]
    val destBase = destOffs?.getOrNull(fl.destStartIndex) ?: Offset.Zero
    val endOrigin = if (destBounds != null) destBounds.topLeft + destBase - gestureState.boardPos else fl.startOrigin
    val progress = gestureState.flightAnim.value
    val curX = fl.startOrigin.x + (endOrigin.x - fl.startOrigin.x) * progress
    val curY = fl.startOrigin.y + (endOrigin.y - fl.startOrigin.y) * progress

    fl.cards.forEachIndexed { k, card ->
        val startRelY = fl.ys.getOrElse(k) { 0f }
        val endRelY = destOffs?.getOrNull(fl.destStartIndex + k)?.let { it.y - destBase.y } ?: startRelY
        val relY = startRelY + (endRelY - startRelY) * progress
        CardView(
            card = card,
            w = cardW,
            h = cardH,
            back = back,
            dark = dark,
            fourColor = fourColor,
            modifier = Modifier.offset {
                IntOffset(curX.roundToInt(), (curY + relY).roundToInt())
            },
            layoutConfig = layout,
        )
    }
}

/** Renders the floating card stack following the user's pointer during an active drag. */
@Composable
fun DragOverlay(
    drag: DragInfo?,
    activeDragTarget: Pile?,
    cardW: Dp,
    cardH: Dp,
    back: Int,
    dark: Boolean,
    fourColor: Boolean,
    layout: BoardLayoutConfig,
) {
    drag ?: return
    drag.cards.forEachIndexed { k, card ->
        CardView(
            card = card,
            w = cardW,
            h = cardH,
            back = back,
            dark = dark,
            highlight = activeDragTarget != null && k == 0,
            fourColor = fourColor,
            modifier = Modifier.offset {
                IntOffset(
                    (drag.origin.x + drag.offset.x).roundToInt(),
                    (drag.origin.y + drag.offset.y + drag.ys[k]).roundToInt(),
                )
            },
            layoutConfig = layout,
        )
    }
}
