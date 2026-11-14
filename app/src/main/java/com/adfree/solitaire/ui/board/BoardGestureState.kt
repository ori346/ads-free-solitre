package com.adfree.solitaire.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import com.adfree.solitaire.GameViewModel
import com.adfree.solitaire.config.UiConfig
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.game.Engine
import com.adfree.solitaire.game.GameState
import com.adfree.solitaire.game.Hint
import com.adfree.solitaire.game.Pile
import com.adfree.solitaire.game.SolverMove
import com.adfree.solitaire.game.TapTargetScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DRAG_CENTER_Y_FRACTION = 0.4f
private const val MIN_RETURN_FLIGHT_DISTANCE_PX = 1f
private const val SHAKE_FULL_CYCLE_RADIANS = Math.PI * 2.0

/** Active drag snapshot in board-local coordinates. */
data class DragInfo(
    val from: Pile,
    val index: Int,
    val cards: List<Card>,
    val ys: List<Float>,
    val origin: Offset,
    val offset: Offset,
)

/** Active card-glide animation payload between board origins. */
data class FlightInfo(
    val destPile: Pile,
    val destStartIndex: Int,
    val cards: List<Card>,
    val ys: List<Float>,
    val startOrigin: Offset,
)

/** Single-pointer ownership gate preventing conflicting multi-touch gestures. */
class GestureOwner {
    var owner: Any? = null
        private set

    val isBusy: Boolean get() = owner != null

    fun acquire(token: Any): Boolean {
        if (owner == null) {
            owner = token
            return true
        }
        return owner === token
    }

    fun release(token: Any) {
        if (owner === token) {
            owner = null
        }
    }

    fun isOwner(token: Any): Boolean = owner === token
}

/** Euclidean distance from [point] to [rect], returning `0f` when [point] is inside [rect]. */
internal fun distanceToRect(point: Offset, rect: Rect): Float {
    val dx = maxOf(rect.left - point.x, 0f, point.x - rect.right)
    val dy = maxOf(rect.top - point.y, 0f, point.y - rect.bottom)
    return hypot(dx, dy)
}

private fun sourcePileDistance(
    center: Offset,
    drag: DragInfo,
    pileBounds: Map<Pile, Rect>,
    boardPos: Offset,
    cardWpx: Float,
    cardHpx: Float,
): Float {
    val originTopLeft = drag.origin + boardPos
    val stackHeight = cardHpx + (drag.ys.lastOrNull()?.coerceAtLeast(0f) ?: 0f)
    val originRect = Rect(
        originTopLeft.x,
        originTopLeft.y,
        originTopLeft.x + cardWpx,
        originTopLeft.y + stackHeight,
    )
    val originDist = distanceToRect(center, originRect)
    if (drag.from is Pile.Waste) return originDist
    val sourceBounds = pileBounds[drag.from] ?: return originDist
    return minOf(originDist, distanceToRect(center, sourceBounds))
}

/**
 * Finds the nearest legal drop target pile whose expanded window bounds contain the dragged card center.
 * Returns `null` when the dragged card center is on or closer to its source pile [DragInfo.from],
 * ensuring that dragging cards back to their original stack cancels the move cleanly.
 */
fun findDropTarget(
    drag: DragInfo,
    state: GameState,
    pileBounds: Map<Pile, Rect>,
    boardPos: Offset,
    cardWpx: Float,
    cardHpx: Float,
    dropTargetMarginFraction: Float,
): Pile? {
    val center = drag.origin + drag.offset + boardPos + Offset(cardWpx / 2f, cardHpx * DRAG_CENTER_Y_FRACTION)
    val marginX = cardWpx * dropTargetMarginFraction
    val marginY = cardHpx * dropTargetMarginFraction
    val sourceDist = sourcePileDistance(center, drag, pileBounds, boardPos, cardWpx, cardHpx)
    if (sourceDist <= 0f) return null

    return pileBounds.entries
        .filter { (pile, rect) ->
            pile != drag.from &&
                (pile is Pile.Tableau || pile is Pile.Foundation) &&
                Engine.canMove(state, drag.from, drag.index, pile) &&
                Rect(
                    rect.left - marginX,
                    rect.top - marginY,
                    rect.right + marginX,
                    rect.bottom + marginY,
                ).contains(center) &&
                distanceToRect(center, rect) < sourceDist
        }
        .minWithOrNull(
            compareBy<Map.Entry<Pile, Rect>> { (_, rect) -> distanceToRect(center, rect) }
                .thenBy { (_, rect) -> hypot(rect.center.x - center.x, rect.center.y - center.y) },
        )
        ?.key
}

/** Coordinates drag, tap, flight, and invalid-move shake state across the Solitaire board. */
@Stable
class BoardGestureState(
    val vm: GameViewModel,
    config: UiConfig,
    haptic: HapticFeedback,
    private val scope: CoroutineScope,
    hapticsEnabled: Boolean,
) {
    var config: UiConfig = config
    var haptic: HapticFeedback = haptic
    var hapticsEnabled: Boolean = hapticsEnabled
    var cardWpx: Float = 0f
    var cardHpx: Float = 0f

    val pileBounds = HashMap<Pile, Rect>()
    val cardOffsets = HashMap<Pile, List<Offset>>()
    val gestureOwner = GestureOwner()

    var boardPos by mutableStateOf(Offset.Zero)
    var drag by mutableStateOf<DragInfo?>(null)
    var flight by mutableStateOf<FlightInfo?>(null)
    val flightAnim = Animatable(0f)

    var shakenCard by mutableStateOf<Pair<Pile, Int>?>(null)
    val shakeAnim = Animatable(0f)

    fun triggerHaptic(type: HapticFeedbackType = HapticFeedbackType.TextHandleMove) {
        if (hapticsEnabled) haptic.performHapticFeedback(type)
    }

    fun triggerShake(pile: Pile, index: Int) {
        shakenCard = pile to index
        scope.launch {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(
                1f,
                animationSpec = tween(durationMillis = config.animation.invalidShakeDurationMs, easing = FastOutSlowInEasing),
            )
            shakenCard = null
        }
    }

    fun launchFlight(
        destPile: Pile,
        destStartIndex: Int,
        cards: List<Card>,
        ys: List<Float>,
        startOrigin: Offset,
        durationMs: Int,
    ) {
        val info = FlightInfo(
            destPile = destPile,
            destStartIndex = destStartIndex,
            cards = cards,
            ys = ys,
            startOrigin = startOrigin,
        )
        flight = info
        scope.launch {
            flightAnim.snapTo(0f)
            flightAnim.animateTo(1f, animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
            if (flight == info) flight = null
        }
    }

    /** Smoothly returns an incomplete or cancelled drag back to its source pile. */
    fun cancelOrReturnDrag(d: DragInfo?) {
        d ?: return
        if (hypot(d.offset.x, d.offset.y) < MIN_RETURN_FLIGHT_DISTANCE_PX) {
            if (flight?.destPile == d.from) flight = null
            return
        }
        launchFlight(
            destPile = d.from,
            destStartIndex = d.index,
            cards = d.cards,
            ys = d.ys,
            startOrigin = d.origin + d.offset,
            durationMs = config.animation.dragReturnFlightDurationMs,
        )
    }

    fun cardBoardOrigin(pile: Pile, idx: Int): Offset? {
        val bounds = pileBounds[pile] ?: return null
        val off = cardOffsets[pile]?.getOrNull(idx) ?: Offset.Zero
        return bounds.topLeft + off - boardPos
    }

    fun findActiveDropTarget(d: DragInfo): Pile? = findDropTarget(
        drag = d,
        state = vm.state,
        pileBounds = pileBounds,
        boardPos = boardPos,
        cardWpx = cardWpx,
        cardHpx = cardHpx,
        dropTargetMarginFraction = config.layout.dropTargetMarginFraction,
    )

    private fun buildMovingStackPayload(src: List<Card>, fromPile: Pile, startIndex: Int): Pair<List<Card>, List<Float>> {
        val movingCards = src.drop(startIndex).map { it.copy(faceUp = true) }
        val offs = cardOffsets[fromPile].orEmpty()
        val baseOff = offs.getOrElse(startIndex) { Offset.Zero }
        val ys = (startIndex until src.size).map { offs.getOrElse(it) { baseOff }.y - baseOff.y }
        return movingCards to ys
    }

    private fun topDrawnWasteCard(state: GameState): Pair<Card, Int>? {
        if (state.stock.isEmpty()) return null
        val drawCount = min(state.drawCount, state.stock.size)
        val topCard = state.stock[state.stock.size - drawCount].copy(faceUp = true)
        val destIdx = state.waste.size + drawCount - 1
        return topCard to destIdx
    }

    fun animateAndTap(pile: Pile, index: Int): Boolean {
        if (pile == Pile.Stock) return false
        val currentState = vm.state
        val (resolvedIdx, target) = vm.resolveTapMove(pile, index) ?: return false
        val src = currentState.pile(pile)
        if (resolvedIdx !in src.indices) return false
        val (movingCards, ys) = buildMovingStackPayload(src, pile, resolvedIdx)
        val startOrigin = cardBoardOrigin(pile, resolvedIdx)
        val destStartIdx = currentState.pile(target).size
        val moved = vm.tapMove(pile, resolvedIdx, target)
        if (moved && startOrigin != null) {
            launchFlight(
                destPile = target,
                destStartIndex = destStartIdx,
                cards = movingCards,
                ys = ys,
                startOrigin = startOrigin,
                durationMs = config.animation.tapFlightDurationMs,
            )
        }
        return moved
    }

    fun animateAndDraw(): Boolean {
        val currentState = vm.state
        val drawnTarget = topDrawnWasteCard(currentState)
        val stockOrigin = pileBounds[Pile.Stock]?.let { it.topLeft - boardPos }
        val moved = vm.draw()
        if (moved && drawnTarget != null && stockOrigin != null) {
            val (topCard, destIdx) = drawnTarget
            launchFlight(
                destPile = Pile.Waste,
                destStartIndex = destIdx,
                cards = listOf(topCard),
                ys = listOf(0f),
                startOrigin = stockOrigin,
                durationMs = config.animation.drawFlightDurationMs,
            )
        }
        return moved
    }

    fun onStockTap() {
        if (!gestureOwner.isBusy && !vm.solverPlaying && animateAndDraw()) {
            triggerHaptic()
        }
    }

    /** Launches a glide animation for a single auto-complete or solver playback transition from [prev] to [curr]. */
    fun handleAutoCompleteFlight(prev: GameState, curr: GameState) {
        if (prev === curr) return
        if (vm.solverPlaying) {
            handleSolverStepFlight(prev)
            return
        }
        if (!vm.autoCompleting) return
        val grownFoundation = Engine.allFoundations.firstOrNull { f ->
            curr.pile(f).size == prev.pile(f).size + 1
        } ?: return
        val movedCard = curr.pile(grownFoundation).last()
        val sources = listOf(Pile.Waste) + Engine.allTableaus
        val fromPile = sources.firstOrNull { p ->
            val prevPile = prev.pile(p)
            prevPile.isNotEmpty() && prevPile.last().id == movedCard.id && curr.pile(p).size == prevPile.size - 1
        } ?: return
        val fromIdx = prev.pile(fromPile).lastIndex
        val startOrigin = cardBoardOrigin(fromPile, fromIdx) ?: return
        launchFlight(
            destPile = grownFoundation,
            destStartIndex = curr.pile(grownFoundation).lastIndex,
            cards = listOf(movedCard),
            ys = listOf(0f),
            startOrigin = startOrigin,
            durationMs = config.animation.autoCompleteFlightDurationMs,
        )
    }

    private fun handleSolverStepFlight(prev: GameState) {
        val move = vm.lastSolverMove ?: return
        when (move) {
            SolverMove.Draw -> {
                triggerHaptic()
                val (topCard, destIdx) = topDrawnWasteCard(prev) ?: return
                val stockOrigin = pileBounds[Pile.Stock]?.let { it.topLeft - boardPos } ?: return
                launchFlight(
                    destPile = Pile.Waste,
                    destStartIndex = destIdx,
                    cards = listOf(topCard),
                    ys = listOf(0f),
                    startOrigin = stockOrigin,
                    durationMs = config.animation.drawFlightDurationMs,
                )
            }
            is SolverMove.Move -> {
                val src = prev.pile(move.from)
                if (move.index !in src.indices) return
                val (movingCards, ys) = buildMovingStackPayload(src, move.from, move.index)
                val startOrigin = cardBoardOrigin(move.from, move.index) ?: return
                val destStartIdx = prev.pile(move.to).size
                triggerHaptic()
                launchFlight(
                    destPile = move.to,
                    destStartIndex = destStartIdx,
                    cards = movingCards,
                    ys = ys,
                    startOrigin = startOrigin,
                    durationMs = config.animation.solverFlightDurationMs,
                )
            }
        }
    }

    fun isCardHidden(pile: Pile, index: Int): Boolean =
        drag?.let { it.from == pile && index >= it.index } == true ||
            flight?.let { it.destPile == pile && index >= it.destStartIndex } == true

    fun isCardHighlighted(
        pile: Pile,
        index: Int,
        size: Int,
        hint: Hint?,
        activeDragTarget: Pile?,
    ): Boolean =
        (hint != null && ((hint.from == pile && index >= hint.index) || (hint.to == pile && index == size - 1))) ||
            (activeDragTarget == pile && index == size - 1)

    fun shakeOffsetPx(pile: Pile, index: Int, density: Density): Int {
        val shaken = shakenCard ?: return 0
        if (shaken.first != pile || index < shaken.second) return 0
        val progress = shakeAnim.value
        val damping = 1f - progress
        val amplitudePx = with(density) { config.animation.invalidShakeAmplitude.toPx() }
        return (sin(progress * SHAKE_FULL_CYCLE_RADIANS) * damping * amplitudePx).roundToInt()
    }

    internal fun tryStartDrag(pile: Pile, index: Int, totalDrag: Offset): Boolean {
        val currentState = vm.state
        val src = currentState.pile(pile)
        val bounds = pileBounds[pile]
        val dragIdx = TapTargetScorer.resolveDraggableStackStartIndex(currentState, pile, index)
        val isAutomated = vm.autoCompleting || vm.solverPlaying
        if (isAutomated || bounds == null || dragIdx !in src.indices || !Engine.isValidRun(src.drop(dragIdx))) {
            return false
        }
        if (flight?.destPile == pile) {
            flight = null
        }
        val (_, ys) = buildMovingStackPayload(src, pile, dragIdx)
        val base = cardOffsets[pile]?.getOrNull(dragIdx) ?: Offset.Zero
        drag = DragInfo(
            from = pile,
            index = dragIdx,
            cards = src.drop(dragIdx),
            ys = ys,
            origin = bounds.topLeft + base - boardPos,
            offset = totalDrag,
        )
        return true
    }

    internal fun updateDragOffset(delta: Offset) {
        drag = drag?.let { it.copy(offset = it.offset + delta) }
    }

    internal fun finishPointerGesture(pile: Pile, index: Int, isDragging: Boolean) {
        if (!isDragging) {
            performTapOrShake(pile, index)
            return
        }
        val d = drag ?: return
        val target = findActiveDropTarget(d)
        val releaseOrigin = d.origin + d.offset
        if (target != null) {
            val destStartIdx = vm.state.pile(target).size
            if (vm.move(d.from, d.index, target)) {
                triggerHaptic()
                launchFlight(
                    destPile = target,
                    destStartIndex = destStartIdx,
                    cards = d.cards,
                    ys = d.ys,
                    startOrigin = releaseOrigin,
                    durationMs = config.animation.dragDropFlightDurationMs,
                )
            } else {
                cancelOrReturnDrag(d)
            }
        } else {
            cancelOrReturnDrag(d)
        }
    }

    private fun performTapOrShake(pile: Pile, index: Int) {
        if (animateAndTap(pile, index)) {
            triggerHaptic()
        } else {
            triggerShake(pile, index)
        }
    }
}

/** Remembers and keeps [BoardGestureState] synchronized with the latest composition settings. */
@Composable
fun rememberBoardGestureState(
    vm: GameViewModel,
    config: UiConfig,
    hapticsEnabled: Boolean,
): BoardGestureState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = remember(vm, scope) {
        BoardGestureState(vm = vm, config = config, haptic = haptic, scope = scope, hapticsEnabled = hapticsEnabled)
    }
    SideEffect {
        state.config = config
        state.haptic = haptic
        state.hapticsEnabled = hapticsEnabled
    }
    return state
}

/** Records window bounds of a board pile container for hit-testing and card flights. */
fun Modifier.trackPile(gestureState: BoardGestureState, pile: Pile): Modifier =
    onGloballyPositioned { gestureState.pileBounds[pile] = it.boundsInWindow() }

/** Attaches single-pointer tap and drag-and-drop gesture recognition to a card at [pile]/[index]. */
fun Modifier.cardPointerGestures(
    gestureState: BoardGestureState,
    pile: Pile,
    index: Int,
): Modifier = pointerInput(pile, index) {
    var totalDrag = Offset.Zero
    var isDragging = false
    var gestureCompleted = false
    var activeToken: Any? = null
    try {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                val token = Any()
                if (!gestureState.gestureOwner.acquire(token)) {
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    continue
                }
                down.consume()
                activeToken = token
                totalDrag = Offset.Zero
                isDragging = false
                gestureCompleted = false
                var activePointerId = down.id
                val touchSlopPx = viewConfiguration.touchSlop * gestureState.config.layout.touchSlopMultiplier

                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == activePointerId }
                        if (change == null || change.isConsumed) {
                            if (isDragging) gestureState.cancelOrReturnDrag(gestureState.drag)
                            gestureCompleted = true
                            break
                        }
                        activePointerId = change.id

                        if (!change.pressed) {
                            change.consume()
                            gestureState.finishPointerGesture(pile, index, isDragging)
                            gestureCompleted = true
                            break
                        }

                        val delta = change.position - change.previousPosition
                        if (delta != Offset.Zero) {
                            totalDrag += delta
                            change.consume()
                            if (!isDragging && hypot(totalDrag.x, totalDrag.y) > touchSlopPx) {
                                isDragging = gestureState.tryStartDrag(pile, index, totalDrag)
                            } else if (isDragging) {
                                gestureState.updateDragOffset(delta)
                            }
                        }
                    }
                } finally {
                    releaseOwnedGesture(gestureState, token, shouldReturnDrag = !gestureCompleted && isDragging)
                    gestureCompleted = true
                    activeToken = null
                }
            }
        }
    } finally {
        val token = activeToken
        if (token != null && gestureState.gestureOwner.isOwner(token)) {
            releaseOwnedGesture(gestureState, token, shouldReturnDrag = !gestureCompleted && isDragging)
        } else if (gestureState.drag?.from == pile) {
            gestureState.drag = null
        }
        activeToken = null
    }
}

private fun releaseOwnedGesture(
    gestureState: BoardGestureState,
    token: Any,
    shouldReturnDrag: Boolean,
) {
    if (shouldReturnDrag) {
        gestureState.cancelOrReturnDrag(gestureState.drag)
    }
    if (gestureState.gestureOwner.isOwner(token)) {
        gestureState.drag = null
        gestureState.gestureOwner.release(token)
    }
}
