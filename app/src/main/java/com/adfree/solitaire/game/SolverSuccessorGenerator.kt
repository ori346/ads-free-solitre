package com.adfree.solitaire.game

import com.adfree.solitaire.config.SolverConfig

internal data class StockCycleStep(
    val moves: List<SolverMove>,
    val drawCount: Int,
    val recycleCount: Int,
    val state: GameState,
)

/**
 * Generates legal omniscient successor transitions and safe foundation promotions for [SolitaireSolver].
 */
internal object SolverSuccessorGenerator {
    fun generateOmniscientSuccessors(
        s: GameState,
        config: SolverConfig,
        useGreedyStockStep: Boolean,
    ): List<StockCycleStep> {
        val result = ArrayList<StockCycleStep>(DEFAULT_SUCCESSOR_CAPACITY)
        val cycleSteps = opportunisticStockWasteCycle(s, config)

        addImmediateFoundationMoves(s, result)
        addTableauMoves(s, cycleSteps, result)
        if (useGreedyStockStep) {
            addGreedyForwardStockStep(s, config, result)
        }
        addFoundationToTableauMoves(s, cycleSteps, config, result)
        addStockWasteMoves(s, cycleSteps, result)

        return result
    }

    fun autoAdvanceSafeFoundationsWithMoves(
        s: GameState,
        config: SolverConfig,
    ): Pair<GameState, List<SolverMove>> {
        var curr = s
        val moves = mutableListOf<SolverMove>()
        var progressed = true
        while (progressed) {
            progressed = false
            val sources = GameRules.rankOrderedExposedSourcePiles(curr)
            for (source in sources) {
                val pile = curr.pile(source)
                val idx = pile.lastIndex
                val card = pile[idx]
                val f = GameRules.foundationTargetFor(curr, card)
                if (f != null && isSafeFoundationMove(curr, card, config)) {
                    val next = GameRules.move(curr, source, idx, f)
                    if (next != null) {
                        moves.add(SolverMove.Move(source, idx, f))
                        curr = next
                        progressed = true
                        break
                    }
                }
            }
        }
        return curr to moves
    }

    private fun isSafeFoundationMove(s: GameState, c: Card, config: SolverConfig): Boolean {
        if (c.rank <= config.safeFoundationRankThreshold) return true
        val oppositeColorsMinRank = Suit.entries
            .filter { it.isRed != c.suit.isRed }
            .minOf { suit -> GameRules.foundationSize(s, suit) }
        if (c.rank <= oppositeColorsMinRank + 1) return true
        val childRank = c.rank - 1
        return Suit.entries
            .filter { it.isRed != c.suit.isRed }
            .all { oppSuit ->
                val child = Card(oppSuit, childRank)
                GameRules.foundationSize(s, oppSuit) >= childRank ||
                    isCardRestingOnValidTableauParent(s, child) ||
                    isSameColorPartnerOpenOnTableau(s, c) ||
                    (c.rank == Card.RANK_THREE && !isCardBlockingHiddenTableau(s, child))
            }
    }

    private fun isCardRestingOnValidTableauParent(s: GameState, child: Card): Boolean {
        for (col in s.tableau) {
            for (idx in 1 until col.size) {
                val curr = col[idx]
                if (curr.id == child.id && curr.faceUp) {
                    val parent = col[idx - 1]
                    return parent.faceUp && parent.rank == child.rank + 1 && parent.hasOppositeColor(child)
                }
            }
        }
        return false
    }

    private fun isSameColorPartnerOpenOnTableau(s: GameState, c: Card): Boolean =
        s.tableau.any { col ->
            val top = col.lastOrNull()
            top != null && top.faceUp && top.suit != c.suit && top.suit.isRed == c.suit.isRed && top.rank == c.rank
        }

    private fun isCardBlockingHiddenTableau(s: GameState, card: Card): Boolean =
        s.tableau.any { col ->
            val firstUp = col.indexOfFirst { it.faceUp }
            firstUp > 0 && col[firstUp].id == card.id
        }

    private fun addGreedyForwardStockStep(
        s: GameState,
        config: SolverConfig,
        out: MutableList<StockCycleStep>,
    ) {
        if (s.stock.isEmpty()) return
        var curr = s
        val moves = ArrayList<SolverMove>()
        var draws = 0
        var plays = 0
        val initialHidden = s.hiddenTableauCardCount
        var emittedFirstFlip = false

        while (curr.stock.isNotEmpty()) {
            val drawn = GameRules.draw(curr, applyPenalty = false) ?: break
            draws++
            moves.add(SolverMove.Draw)
            val (afterSafe, safeMoves) = autoAdvanceSafeFoundationsWithMoves(drawn, config)
            if (safeMoves.isNotEmpty()) {
                moves.addAll(safeMoves)
                plays += safeMoves.size
            }
            curr = afterSafe

            var progressed = true
            while (progressed) {
                progressed = false
                for (from in GameRules.allTableaus) {
                    val col = curr.pile(from)
                    val firstUp = col.indexOfFirst { it.faceUp }
                    if (firstUp <= 0) continue
                    val target = GameRules.allTableaus.firstOrNull { to ->
                        to != from &&
                            GameRules.canMove(curr, from, firstUp, to) &&
                            (curr.pile(to).isEmpty() || !GameRules.canMoveCardToAnyFoundation(curr, curr.pile(to).last()))
                    } ?: continue
                    val next = GameRules.move(curr, from, firstUp, target) ?: continue
                    moves.add(SolverMove.Move(from, firstUp, target))
                    val (adv, sm) = autoAdvanceSafeFoundationsWithMoves(next, config)
                    if (sm.isNotEmpty()) moves.addAll(sm)
                    curr = adv
                    plays++
                    progressed = true
                    break
                }
                if (progressed) continue

                if (curr.waste.isEmpty()) break
                val wIdx = curr.waste.lastIndex
                val wCard = curr.waste[wIdx]
                val f = GameRules.foundationTargetFor(curr, wCard)
                if (f != null && !isCardNeededForHiddenPivot(curr, wCard)) {
                    val next = GameRules.move(curr, Pile.Waste, wIdx, f)
                    if (next != null) {
                        moves.add(SolverMove.Move(Pile.Waste, wIdx, f))
                        val (adv, sm) = autoAdvanceSafeFoundationsWithMoves(next, config)
                        if (sm.isNotEmpty()) moves.addAll(sm)
                        curr = adv
                        plays++
                        progressed = true
                        continue
                    }
                }

                val dest = selectGreedyWasteTableauTarget(curr, wIdx, wCard)
                if (dest != null) {
                    val next = GameRules.move(curr, Pile.Waste, wIdx, dest)
                    if (next != null) {
                        moves.add(SolverMove.Move(Pile.Waste, wIdx, dest))
                        val (adv, sm) = autoAdvanceSafeFoundationsWithMoves(next, config)
                        if (sm.isNotEmpty()) moves.addAll(sm)
                        curr = adv
                        plays++
                        progressed = true
                    }
                }
            }

            if (!emittedFirstFlip && curr.hiddenTableauCardCount < initialHidden) {
                emittedFirstFlip = true
                out.add(StockCycleStep(ArrayList(moves), draws, 0, curr))
            }
        }

        if (draws > 0 && plays >= MIN_GREEDY_STOCK_PLAYS) {
            out.add(StockCycleStep(moves, draws, 0, curr))
        }
    }

    private fun selectGreedyWasteTableauTarget(
        s: GameState,
        wIdx: Int,
        wCard: Card,
    ): Pile.Tableau? {
        val candidates = GameRules.allTableaus.filter { to ->
            if (!GameRules.canMove(s, Pile.Waste, wIdx, to)) return@filter false
            val destCol = s.pile(to)
            if (destCol.isEmpty()) {
                unlocksHiddenPivot(s, wCard) ||
                    (wIdx > 0 && GameRules.canMoveCardToAnyFoundation(s, s.waste[wIdx - 1]))
            } else {
                !GameRules.canMoveCardToAnyFoundation(s, destCol.last())
            }
        }
        return candidates.maxByOrNull { to ->
            val destCol = s.pile(to)
            val hiddenCount = destCol.count { !it.faceUp }
            (if (unlocksHiddenPivot(s, wCard)) GREEDY_UNLOCK_PIVOT_SCORE_BONUS else 0) + hiddenCount
        }
    }

    private fun unlocksHiddenPivot(s: GameState, parentCandidate: Card): Boolean =
        s.tableau.any { col ->
            val firstUp = col.indexOfFirst { it.faceUp }
            firstUp > 0 &&
                col[firstUp].rank == parentCandidate.rank - 1 &&
                col[firstUp].hasOppositeColor(parentCandidate)
        }

    private fun isCardNeededForHiddenPivot(s: GameState, c: Card): Boolean =
        c.rank >= Card.RANK_THREE && unlocksHiddenPivot(s, c) && !isSameColorPartnerOpenOnTableau(s, c)

    /**
     * Steps through the stock/waste cycle while greedily promoting any safe foundation cards
     * (Aces, 2s, and balanced foundation ranks) that appear on top of waste at each draw step.
     */
    private fun opportunisticStockWasteCycle(
        s: GameState,
        config: SolverConfig,
    ): List<StockCycleStep> {
        if (s.stock.isEmpty() && s.waste.isEmpty()) return emptyList()
        val steps = ArrayList<StockCycleStep>()
        val visited = HashSet<Long>()
        var curr = s
        visited.add(curr.boardHash())
        steps.add(StockCycleStep(moves = emptyList(), drawCount = 0, recycleCount = 0, state = curr))

        val accumMoves = ArrayList<SolverMove>()
        var draws = 0
        var recycles = 0
        val maxDraws = (s.stock.size + s.waste.size) * STOCK_CYCLE_MULTIPLIER + STOCK_CYCLE_BASE_MARGIN
        while (draws < maxDraws) {
            if (curr.stock.isEmpty() && curr.waste.isEmpty()) break
            val isRecycle = curr.stock.isEmpty()
            val drawn = GameRules.draw(curr, applyPenalty = false) ?: break
            draws++
            if (isRecycle) recycles++
            accumMoves.add(SolverMove.Draw)

            val (advanced, safeMoves) = autoAdvanceSafeFoundationsWithMoves(drawn, config)
            if (safeMoves.isNotEmpty()) {
                accumMoves.addAll(safeMoves)
            }
            curr = advanced
            if (!visited.add(curr.boardHash())) break
            steps.add(
                StockCycleStep(
                    moves = ArrayList(accumMoves),
                    drawCount = draws,
                    recycleCount = recycles,
                    state = curr,
                ),
            )
        }

        // In Draw-3 mode, append any raw stock/waste alignment states not reached by the greedy pass.
        if (s.drawCount > 1) {
            val seenStockWaste = steps.mapTo(HashSet()) { it.state.stockWasteHash() }
            var rawRecycles = 0
            var prevStockEmpty = s.stock.isEmpty()
            for ((rawDraws, rawState) in GameRules.stockWasteCycleStates(s)) {
                if (rawDraws == 0) continue
                if (prevStockEmpty) rawRecycles++
                prevStockEmpty = rawState.stock.isEmpty()
                if (seenStockWaste.add(rawState.stockWasteHash())) {
                    steps.add(
                        StockCycleStep(
                            moves = List(rawDraws) { SolverMove.Draw },
                            drawCount = rawDraws,
                            recycleCount = rawRecycles,
                            state = rawState,
                        ),
                    )
                }
            }
        }
        return steps
    }

    private fun addImmediateFoundationMoves(
        s: GameState,
        out: MutableList<StockCycleStep>,
    ) {
        if (s.waste.isNotEmpty()) {
            val wIdx = s.waste.lastIndex
            GameRules.foundationTargetFor(s, s.waste[wIdx])?.let { f ->
                GameRules.move(s, Pile.Waste, wIdx, f)?.let { next ->
                    out.add(StockCycleStep(listOf(SolverMove.Move(Pile.Waste, wIdx, f)), 0, 0, next))
                }
            }
        }
        for (t in GameRules.allTableaus) {
            val col = s.pile(t)
            if (col.isEmpty()) continue
            val topIdx = col.lastIndex
            GameRules.foundationTargetFor(s, col[topIdx])?.let { f ->
                GameRules.move(s, t, topIdx, f)?.let { next ->
                    out.add(StockCycleStep(listOf(SolverMove.Move(t, topIdx, f)), 0, 0, next))
                }
            }
        }
    }

    private fun addTableauMoves(
        s: GameState,
        cycleSteps: List<StockCycleStep>,
        out: MutableList<StockCycleStep>,
    ) {
        val kingInTableauWithHidden = GameRules.allTableaus.any { t ->
            val col = s.pile(t)
            val firstUp = col.indexOfFirst { it.faceUp }
            firstUp > 0 && col[firstUp].isKing
        }
        val kingWaiting = kingInTableauWithHidden ||
            cycleSteps.any { step -> step.state.waste.lastOrNull()?.isKing == true }

        for (from in GameRules.allTableaus) {
            val col = s.pile(from)
            if (col.isEmpty()) continue
            val firstUp = col.indexOfFirst { it.faceUp }
            if (firstUp < 0) continue

            val revealsHidden = firstUp > 0
            val emptiesForKing = firstUp == 0 && kingWaiting
            if (revealsHidden || emptiesForKing) {
                emitTableauMoveTargets(s, from, firstUp, skipEmpty = firstUp == 0, out = out)
            }

            // Split a face-up stack only to expose an immediate foundation card or break non-descending pairs.
            for (idx in (firstUp + 1) until col.size) {
                val uncovered = col[idx - 1]
                val breaksBadPair = !GameRules.isValidRun(listOf(uncovered, col[idx]))
                if (GameRules.canMoveCardToAnyFoundation(s, uncovered) || breaksBadPair) {
                    emitTableauMoveTargets(s, from, idx, skipEmpty = false, out = out)
                }
            }
        }
    }

    private fun emitTableauMoveTargets(
        s: GameState,
        from: Pile.Tableau,
        index: Int,
        skipEmpty: Boolean,
        out: MutableList<StockCycleStep>,
    ) {
        var movedToEmpty = false
        for (to in GameRules.allTableaus) {
            if (to == from) continue
            if (s.pile(to).isEmpty()) {
                if (skipEmpty || movedToEmpty) continue
                movedToEmpty = true
            }
            GameRules.move(s, from, index, to)?.let { next ->
                out.add(StockCycleStep(listOf(SolverMove.Move(from, index, to)), 0, 0, next))
            }
        }
    }

    private fun addFoundationToTableauMoves(
        s: GameState,
        cycleSteps: List<StockCycleStep>,
        config: SolverConfig,
        out: MutableList<StockCycleStep>,
    ) {
        for (f in GameRules.allFoundations) {
            val fPile = s.pile(f)
            if (fPile.isEmpty()) continue
            val card = fPile.last()
            if (isSafeFoundationMove(s, card, config)) continue
            val childRank = card.rank - 1
            if (childRank < Card.RANK_ACE) continue

            val childBlockedOnTableau = GameRules.allTableaus.any { t ->
                val col = s.pile(t)
                col.withIndex().any { (idx, tc) ->
                    idx > 0 && tc.faceUp && tc.rank == childRank && tc.hasOppositeColor(card)
                }
            }
            val childInStockCycle = !childBlockedOnTableau &&
                cycleSteps.any { step ->
                    step.state.waste.lastOrNull()?.let { it.rank == childRank && it.hasOppositeColor(card) } == true
                }
            if (!childBlockedOnTableau && !childInStockCycle) continue

            var movedToEmpty = false
            val fIdx = fPile.lastIndex
            for (to in GameRules.allTableaus) {
                if (s.pile(to).isEmpty()) {
                    if (movedToEmpty) continue
                    movedToEmpty = true
                }
                GameRules.move(s, f, fIdx, to)?.let { next ->
                    out.add(StockCycleStep(listOf(SolverMove.Move(f, fIdx, to)), 0, 0, next))
                }
            }
        }
    }

    private fun addStockWasteMoves(
        initialState: GameState,
        cycleSteps: List<StockCycleStep>,
        out: MutableList<StockCycleStep>,
    ) {
        var lastSafeHarvestStep: StockCycleStep? = null
        var prevFoundationCount = initialState.foundationCardCount

        for (step in cycleSteps) {
            val swState = step.state
            if (swState.foundationCardCount > prevFoundationCount) {
                lastSafeHarvestStep = step
                prevFoundationCount = swState.foundationCardCount
            }
            if (swState.waste.isEmpty()) continue
            val wIdx = swState.waste.lastIndex
            val wasteCard = swState.waste[wIdx]

            if (step.drawCount > 0) {
                GameRules.foundationTargetFor(swState, wasteCard)?.let { f ->
                    GameRules.move(swState, Pile.Waste, wIdx, f)?.let { next ->
                        out.add(
                            StockCycleStep(
                                moves = step.moves + SolverMove.Move(Pile.Waste, wIdx, f),
                                drawCount = step.drawCount,
                                recycleCount = step.recycleCount,
                                state = next,
                            ),
                        )
                    }
                }
            }

            var movedToEmpty = false
            for (to in GameRules.allTableaus) {
                if (swState.pile(to).isEmpty()) {
                    if (movedToEmpty) continue
                    movedToEmpty = true
                }
                GameRules.move(swState, Pile.Waste, wIdx, to)?.let { next ->
                    out.add(
                        StockCycleStep(
                            moves = step.moves + SolverMove.Move(Pile.Waste, wIdx, to),
                            drawCount = step.drawCount,
                            recycleCount = step.recycleCount,
                            state = next,
                        ),
                    )
                }
            }
        }

        // Also allow harvesting safe foundation cards (Aces/2s) from the stock even without a tableau play.
        if (lastSafeHarvestStep != null && lastSafeHarvestStep.drawCount > 0) {
            out.add(lastSafeHarvestStep)
        }
    }

    private const val DEFAULT_SUCCESSOR_CAPACITY = 16
    private const val MIN_GREEDY_STOCK_PLAYS = 2
    private const val GREEDY_UNLOCK_PIVOT_SCORE_BONUS = 100
    private const val STOCK_CYCLE_MULTIPLIER = 2
    private const val STOCK_CYCLE_BASE_MARGIN = 4
}
