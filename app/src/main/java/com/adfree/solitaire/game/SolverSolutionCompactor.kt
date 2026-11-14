package com.adfree.solitaire.game

import com.adfree.solitaire.config.SolverConfig

/**
 * Replays and compacts verified winning solver move sequences to eliminate redundant stock cycles
 * and promote exposed foundation cards as early as possible.
 */
internal object SolverSolutionCompactor {
    private data class PlannedTableauMove(
        val movingCardId: Int,
        val parentCardId: Int,
        val fromWaste: Boolean,
        var completed: Boolean = false,
    )

    /**
     * Replays and compacts a verified winning move sequence by greedily promoting cards to
     * foundations as soon as they have no remaining tableau children, and executing planned
     * waste-to-tableau and tableau-to-tableau attachments during the earliest possible stock pass.
     */
    fun compactSolution(
        initial: GameState,
        rawMoves: List<SolverMove>,
        config: SolverConfig,
    ): List<SolverMove> {
        var best = rawMoves
        for (pass in 0 until MAX_COMPACT_PASSES) {
            val candidates = listOfNotNull(
                runSingleCompactionPass(initial, best, config, skipLeafWasteParks = false),
                runSingleCompactionPass(initial, best, config, skipLeafWasteParks = true),
            )
            val shortest = candidates.minByOrNull { it.size } ?: break
            if (shortest.size < best.size) {
                best = shortest
            } else {
                break
            }
        }
        return best
    }

    private fun runSingleCompactionPass(
        initial: GameState,
        rawMoves: List<SolverMove>,
        config: SolverConfig,
        skipLeafWasteParks: Boolean,
    ): List<SolverMove>? {
        val rawPlans = ArrayList<PlannedTableauMove>()
        var sim = initial
        for (move in rawMoves) {
            sim = when (move) {
                SolverMove.Draw -> GameRules.draw(sim, applyPenalty = false) ?: return null
                is SolverMove.Move -> {
                    val src = sim.pile(move.from)
                    if (move.index !in src.indices) return null
                    val movingCard = src[move.index]
                    if (move.to is Pile.Tableau) {
                        val destPile = sim.pile(move.to)
                        val parentId = destPile.lastOrNull()?.id ?: EMPTY_PARENT_ID
                        rawPlans.add(
                            PlannedTableauMove(
                                movingCardId = movingCard.id,
                                parentCardId = parentId,
                                fromWaste = move.from == Pile.Waste,
                            ),
                        )
                    }
                    GameRules.move(sim, move.from, move.index, move.to) ?: return null
                }
            }
        }
        if (!sim.isWon) return null

        val plannedMoves = if (skipLeafWasteParks) {
            val everUsedAsParent = BooleanArray(Card.DECK_SIZE)
            for (p in rawPlans) {
                if (p.parentCardId >= 0) everUsedAsParent[p.parentCardId] = true
            }
            ArrayList(rawPlans.filter { !it.fromWaste || everUsedAsParent[it.movingCardId] })
        } else {
            rawPlans
        }

        val pendingParentUses = IntArray(Card.DECK_SIZE)
        for (p in plannedMoves) {
            if (p.parentCardId >= 0) pendingParentUses[p.parentCardId]++
        }

        var curr = initial
        val outMoves = ArrayList<SolverMove>(rawMoves.size)
        var bestCompleteCandidate: List<SolverMove>? = null
        val maxSteps = rawMoves.size + COMPACT_SIMULATION_STEP_MARGIN

        var steps = 0
        while (!curr.isWon && steps < maxSteps) {
            steps++
            if (curr.areAllTableauCardsFaceUp) {
                AutoCompleter.simulateAutoCompleteWithMoves(curr, config)?.let { (_, autoMoves) ->
                    val candidate = outMoves + autoMoves
                    if (bestCompleteCandidate == null || candidate.size < bestCompleteCandidate.size) {
                        bestCompleteCandidate = candidate
                    }
                }
            }

            // 1. Greedily promote any exposed card to foundation if no future tableau move lands on it.
            val promoted = tryPromoteReadyFoundationCard(curr, pendingParentUses, plannedMoves, outMoves)
            if (promoted != null) {
                curr = promoted
                continue
            }

            // 2. Execute any ready planned tableau-to-tableau move.
            val tabMoved = tryExecutePlannedTableauMove(curr, pendingParentUses, plannedMoves, outMoves)
            if (tabMoved != null) {
                curr = tabMoved
                continue
            }

            // 3. Execute a ready planned waste-to-tableau move for the current waste top card.
            val wasteMoved = tryExecutePlannedWasteMove(curr, pendingParentUses, plannedMoves, outMoves)
            if (wasteMoved != null) {
                curr = wasteMoved
                continue
            }

            // 4. Advance stock by 1 draw step.
            val drawn = GameRules.draw(curr, applyPenalty = false) ?: break
            outMoves.add(SolverMove.Draw)
            curr = drawn
        }

        val finishedDirect = if (curr.isWon) outMoves else null
        return when {
            finishedDirect != null && bestCompleteCandidate != null ->
                if (finishedDirect.size <= bestCompleteCandidate.size) finishedDirect else bestCompleteCandidate
            finishedDirect != null -> finishedDirect
            else -> bestCompleteCandidate
        }
    }

    private fun tryPromoteReadyFoundationCard(
        curr: GameState,
        pendingParentUses: IntArray,
        plannedMoves: List<PlannedTableauMove>,
        outMoves: MutableList<SolverMove>,
    ): GameState? {
        val sources = GameRules.rankOrderedExposedSourcePiles(curr)
        for (source in sources) {
            val pile = curr.pile(source)
            val idx = pile.lastIndex
            val card = pile[idx]
            if (pendingParentUses[card.id] > 0) continue
            val f = GameRules.foundationTargetFor(curr, card) ?: continue
            val next = GameRules.move(curr, source, idx, f) ?: continue
            outMoves.add(SolverMove.Move(source, idx, f))
            // Cancel any remaining planned tableau moves for this card since it is now on its foundation.
            for (plan in plannedMoves) {
                if (!plan.completed && plan.movingCardId == card.id) {
                    plan.completed = true
                    if (plan.parentCardId >= 0) pendingParentUses[plan.parentCardId]--
                }
            }
            return next
        }
        return null
    }

    private fun tryExecutePlannedTableauMove(
        curr: GameState,
        pendingParentUses: IntArray,
        plannedMoves: List<PlannedTableauMove>,
        outMoves: MutableList<SolverMove>,
    ): GameState? {
        for (plan in plannedMoves) {
            if (plan.completed || plan.fromWaste) continue
            if (hasEarlierIncompletePlanForCard(plannedMoves, plan)) continue
            if (hasEarlierIncompletePlanForParent(plannedMoves, plan)) continue
            val (from, index) = findFaceUpCardInTableau(curr, plan.movingCardId) ?: continue
            val col = curr.pile(from)
            val to = findTargetTableauColumn(curr, from, plan.parentCardId) ?: continue
            if (!GameRules.canMove(curr, from, index, to)) continue
            if (index > 0 && col[index - 1].faceUp) {
                val uncovered = col[index - 1]
                val uncoveredReady =
                    GameRules.canMoveCardToAnyFoundation(curr, uncovered) ||
                        pendingParentUses[uncovered.id] > 0 ||
                        plannedMoves.any { !it.completed && it.movingCardId == uncovered.id }
                if (!uncoveredReady && GameRules.isValidRun(listOf(uncovered, col[index]))) continue
            }
            val next = GameRules.move(curr, from, index, to) ?: continue
            outMoves.add(SolverMove.Move(from, index, to))
            plan.completed = true
            if (plan.parentCardId >= 0) pendingParentUses[plan.parentCardId]--
            return next
        }
        return null
    }

    private fun tryExecutePlannedWasteMove(
        curr: GameState,
        pendingParentUses: IntArray,
        plannedMoves: List<PlannedTableauMove>,
        outMoves: MutableList<SolverMove>,
    ): GameState? {
        if (curr.waste.isEmpty()) return null
        val wIdx = curr.waste.lastIndex
        val wCard = curr.waste[wIdx]
        val plan = plannedMoves.firstOrNull { !it.completed && it.movingCardId == wCard.id } ?: return null
        if (!plan.fromWaste) return null
        if (hasEarlierIncompletePlanForParent(plannedMoves, plan)) return null
        val to = findTargetTableauColumn(curr, from = null, plan.parentCardId) ?: return null
        val next = GameRules.move(curr, Pile.Waste, wIdx, to) ?: return null
        outMoves.add(SolverMove.Move(Pile.Waste, wIdx, to))
        plan.completed = true
        if (plan.parentCardId >= 0) pendingParentUses[plan.parentCardId]--
        return next
    }

    private fun hasEarlierIncompletePlanForCard(
        plannedMoves: List<PlannedTableauMove>,
        target: PlannedTableauMove,
    ): Boolean {
        for (plan in plannedMoves) {
            if (plan === target) return false
            if (!plan.completed && plan.movingCardId == target.movingCardId) return true
        }
        return false
    }

    private fun hasEarlierIncompletePlanForParent(
        plannedMoves: List<PlannedTableauMove>,
        target: PlannedTableauMove,
    ): Boolean {
        if (target.parentCardId < 0) return false
        for (plan in plannedMoves) {
            if (plan === target) return false
            if (!plan.completed && plan.parentCardId == target.parentCardId) return true
        }
        return false
    }

    private fun findFaceUpCardInTableau(curr: GameState, cardId: Int): Pair<Pile.Tableau, Int>? {
        for (t in GameRules.allTableaus) {
            val col = curr.pile(t)
            for (i in col.indices) {
                if (col[i].faceUp && col[i].id == cardId) return t to i
            }
        }
        return null
    }

    private fun findTargetTableauColumn(
        curr: GameState,
        from: Pile.Tableau?,
        parentCardId: Int,
    ): Pile.Tableau? {
        if (parentCardId == EMPTY_PARENT_ID) {
            return GameRules.allTableaus.firstOrNull { it != from && curr.pile(it).isEmpty() }
        }
        return GameRules.allTableaus.firstOrNull { t ->
            t != from && curr.pile(t).lastOrNull()?.let { it.faceUp && it.id == parentCardId } == true
        }
    }

    private const val EMPTY_PARENT_ID = -1
    private const val MAX_COMPACT_PASSES = 3
    private const val COMPACT_SIMULATION_STEP_MARGIN = 40
}
