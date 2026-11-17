package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.SolverConfig
import java.util.PriorityQueue

/**
 * Represents a single legal move step produced by [SolitaireSolver].
 */
sealed interface SolverMove {
    /** Draw from stock (or recycle waste into stock when empty). */
    data object Draw : SolverMove

    /** Move card(s) starting at [index] from [from] onto [to]. */
    data class Move(val from: Pile, val index: Int, val to: Pile) : SolverMove
}

/**
 * Outcome of an omniscient solvability search.
 *
 * @property isSolvable True if a verified winning move sequence was found within the search budget.
 * @property winningMoves Complete sequence of legal [SolverMove]s leading from the initial state to victory.
 * @property exploredStates Number of search states evaluated.
 */
data class SolverResult(
    val isSolvable: Boolean,
    val winningMoves: List<SolverMove> = emptyList(),
    val exploredStates: Int = 0,
)

/**
 * Identifies the transition in a game's undo history where the board changed from solvable to unsolvable.
 *
 * @property historyIndex Index in the combined `(history + currentState)` timeline of [solvableState].
 * @property solvableState The last board snapshot that remained solvable.
 * @property unsolvableState The immediately following snapshot where solvability was lost.
 * @property fatalMove The move transitioning from [solvableState] to [unsolvableState].
 * @property movesToUndo Number of undo steps from the current state back to [solvableState].
 */
data class LastSolvablePoint(
    val historyIndex: Int,
    val solvableState: GameState,
    val unsolvableState: GameState,
    val fatalMove: SolverMove,
    val movesToUndo: Int,
)

/**
 * Omniscient best-first Klondike solver delegating deadlock detection, successor generation,
 * and solution compaction to focused helper modules.
 */
object SolitaireSolver {
    private class SearchNode(
        val state: GameState,
        val parent: SearchNode?,
        val edgeMoves: List<SolverMove>,
        val depth: Int,
        val moveCount: Int,
        val drawMoveCount: Int,
        val recycleCount: Int,
    )

    /**
     * Returns `true` only when [s] is provably deadlocked with zero possibility of victory.
     */
    fun hasStructuralDeadlock(s: GameState): Boolean =
        SolverDeadlockDetector.hasStructuralDeadlock(s)

    /**
     * Searches from [initial] using full card visibility up to [maxStates] explored states
     * and [maxDurationMs] wall-clock milliseconds.
     * When [stopOnFirstSolution] is `true`, returns as soon as any winning state is reached
     * without reconstructing or compacting [SolverResult.winningMoves].
     * Every transition uses legal [GameRules.draw] / [GameRules.move] steps so [SolverResult.winningMoves]
     * is guaranteed to replay cleanly to `isWon == true` whenever [SolverResult.isSolvable] is `true`
     * and [stopOnFirstSolution] is `false`.
     */
    fun solveOmniscient(
        initial: GameState,
        maxStates: Int = DefaultGameConfig.solver.defaultMaxStates,
        maxDurationMs: Long = Long.MAX_VALUE,
        config: SolverConfig = DefaultGameConfig.solver,
        stopOnFirstSolution: Boolean = false,
    ): SolverResult {
        if (initial.isWon) return SolverResult(isSolvable = true, exploredStates = 1)
        if (hasStructuralDeadlock(initial)) return SolverResult(isSolvable = false, exploredStates = 1)

        val (startState, startMoves) = SolverSuccessorGenerator.autoAdvanceSafeFoundationsWithMoves(initial, config)
        val rootNode = SearchNode(
            state = startState,
            parent = null,
            edgeMoves = startMoves,
            depth = 0,
            moveCount = startMoves.size,
            drawMoveCount = 0,
            recycleCount = 0,
        )
        finishIfSolved(rootNode, explored = 1, config, initial, stopOnFirstSolution)?.let { return it }
        if (hasStructuralDeadlock(startState)) return SolverResult(isSolvable = false, exploredStates = 1)

        val hasDeadline = maxDurationMs < Long.MAX_VALUE
        val deadlineNanos = if (hasDeadline) {
            System.nanoTime() + maxDurationMs.coerceAtLeast(0L) * NANOS_PER_MS
        } else {
            Long.MAX_VALUE
        }

        val greedyBudget = (maxStates / GREEDY_BUDGET_DIVISOR).coerceIn(MIN_GREEDY_STATES, MAX_GREEDY_STATES)
        var totalExplored = 0
        var bestSolution: List<SolverMove>? = null

        if (maxStates > MIN_GREEDY_STATES) {
            val greedyRes = runSearchPass(
                initial = initial,
                rootNode = rootNode,
                maxStates = greedyBudget,
                hasDeadline = hasDeadline,
                deadlineNanos = deadlineNanos,
                config = config,
                useGreedyStockStep = true,
                initialBest = null,
                stopOnFirstSolution = stopOnFirstSolution,
            )
            totalExplored += greedyRes.exploredStates
            if (greedyRes.isSolvable) {
                if (stopOnFirstSolution) {
                    return SolverResult(isSolvable = true, exploredStates = totalExplored)
                }
                bestSolution = greedyRes.winningMoves
            }
        }

        val remainingBudget = (maxStates - totalExplored).coerceAtLeast(0)
        if (remainingBudget > 0 && !(hasDeadline && System.nanoTime() >= deadlineNanos)) {
            val standardMaxStates = if (bestSolution != null) {
                remainingBudget.coerceAtMost(
                    if (maxStates >= INTERACTIVE_BUDGET_THRESHOLD) INTERACTIVE_IMPROVEMENT_STATES else DEFAULT_IMPROVEMENT_STATES,
                )
            } else {
                remainingBudget
            }
            val standardRes = runSearchPass(
                initial = initial,
                rootNode = rootNode,
                maxStates = standardMaxStates,
                hasDeadline = hasDeadline,
                deadlineNanos = deadlineNanos,
                config = config,
                useGreedyStockStep = false,
                initialBest = bestSolution,
                stopOnFirstSolution = stopOnFirstSolution,
            )
            totalExplored += standardRes.exploredStates
            if (standardRes.isSolvable) {
                if (stopOnFirstSolution) {
                    return SolverResult(isSolvable = true, exploredStates = totalExplored)
                }
                if (bestSolution == null || standardRes.winningMoves.size < bestSolution.size) {
                    bestSolution = standardRes.winningMoves
                }
            }
        }

        return if (bestSolution != null) {
            SolverResult(isSolvable = true, winningMoves = bestSolution, exploredStates = totalExplored)
        } else {
            SolverResult(isSolvable = false, exploredStates = totalExplored)
        }
    }

    private fun runSearchPass(
        initial: GameState,
        rootNode: SearchNode,
        maxStates: Int,
        hasDeadline: Boolean,
        deadlineNanos: Long,
        config: SolverConfig,
        useGreedyStockStep: Boolean,
        initialBest: List<SolverMove>?,
        stopOnFirstSolution: Boolean,
    ): SolverResult {
        val bestMovesToState = HashMap<Long, Int>()
        val queue = PriorityQueue<Pair<Int, SearchNode>>(
            compareByDescending<Pair<Int, SearchNode>> { it.first }
                .thenBy { it.second.moveCount }
                .thenBy { it.second.drawMoveCount },
        )
        bestMovesToState[rootNode.state.boardHash()] = rootNode.moveCount
        queue.add(priorityScore(rootNode, config) to rootNode)

        var explored = 0
        var bestSolution: List<SolverMove>? = initialBest
        var bestFoundAtExplored = 0
        val improvementWindow = if (maxStates >= INTERACTIVE_BUDGET_THRESHOLD) {
            INTERACTIVE_IMPROVEMENT_STATES
        } else {
            DEFAULT_IMPROVEMENT_STATES
        }

        while (queue.isNotEmpty() && explored < maxStates) {
            if (hasDeadline && (explored and DEADLINE_CHECK_MASK) == 0 && System.nanoTime() >= deadlineNanos) {
                break
            }
            if (bestSolution != null && (stopOnFirstSolution || explored - bestFoundAtExplored >= improvementWindow)) {
                break
            }
            val (_, currNode) = queue.poll() ?: break
            if (!stopOnFirstSolution && bestSolution != null && minTotalMovesLowerBound(currNode) >= bestSolution.size) {
                continue
            }
            val recordedBest = bestMovesToState[currNode.state.boardHash()]
            if (recordedBest != null && currNode.moveCount > recordedBest) {
                continue
            }
            explored++

            finishIfSolved(currNode, explored, config, initial, stopOnFirstSolution)?.let { solved ->
                if (stopOnFirstSolution) return solved
                if (bestSolution == null || solved.winningMoves.size < bestSolution.size) {
                    bestSolution = solved.winningMoves
                    bestFoundAtExplored = explored
                }
            }

            for (successor in SolverSuccessorGenerator.generateOmniscientSuccessors(currNode.state, config, useGreedyStockStep)) {
                val (nextState, safeMoves) = SolverSuccessorGenerator.autoAdvanceSafeFoundationsWithMoves(successor.state, config)
                val combinedMoves = successor.moves + safeMoves
                val nextNode = SearchNode(
                    state = nextState,
                    parent = currNode,
                    edgeMoves = combinedMoves,
                    depth = currNode.depth + 1,
                    moveCount = currNode.moveCount + combinedMoves.size,
                    drawMoveCount = currNode.drawMoveCount + successor.drawCount,
                    recycleCount = currNode.recycleCount + successor.recycleCount,
                )
                if (!stopOnFirstSolution && bestSolution != null && minTotalMovesLowerBound(nextNode) >= bestSolution.size) {
                    continue
                }

                val hash = nextState.boardHash()
                val prevBest = bestMovesToState[hash]
                if (prevBest != null && (bestSolution == null || stopOnFirstSolution || nextNode.moveCount >= prevBest)) {
                    continue
                }

                val solved = finishIfSolved(nextNode, explored, config, initial, stopOnFirstSolution)
                if (solved != null) {
                    if (stopOnFirstSolution) return solved
                    if (bestSolution == null || solved.winningMoves.size < bestSolution.size) {
                        bestSolution = solved.winningMoves
                        bestFoundAtExplored = explored
                    }
                    continue
                }

                if (hasStructuralDeadlock(nextState)) {
                    bestMovesToState[hash] = DEADLOCK_MARKER_MOVES
                    continue
                }

                bestMovesToState[hash] = nextNode.moveCount
                queue.add(priorityScore(nextNode, config) to nextNode)
            }
        }
        return if (bestSolution != null) {
            SolverResult(isSolvable = true, winningMoves = bestSolution, exploredStates = explored)
        } else {
            SolverResult(isSolvable = false, exploredStates = explored)
        }
    }

    /** Convenience wrapper returning only whether [initial] is solvable within [maxStates]. */
    fun isSolvableOmniscient(
        initial: GameState,
        maxStates: Int = DefaultGameConfig.solver.defaultMaxStates,
    ): Boolean = solveOmniscient(initial, maxStates, stopOnFirstSolution = true).isSolvable

    /** Returns the structural [GameState.boardHash] of [s] after safe foundation auto-advances. */
    fun safeBoardHash(
        s: GameState,
        config: SolverConfig = DefaultGameConfig.solver,
    ): Long = SolverSuccessorGenerator.autoAdvanceSafeFoundationsWithMoves(s, config).first.boardHash()

    /**
     * Binary-searches the undo timeline (`history + currentState`) to locate the last solvable
     * state and the single fatal move that rendered the board unsolvable.
     */
    fun findLastSolvablePoint(
        history: List<GameState>,
        currentState: GameState,
        maxStatesPerCheck: Int = DefaultGameConfig.solver.binarySearchMaxStates,
        maxDurationPerCheckMs: Long = DefaultGameConfig.solver.binarySearchMaxDurationMs,
        knownSolvability: MutableMap<Long, Boolean>? = null,
        config: SolverConfig = DefaultGameConfig.solver,
    ): LastSolvablePoint? {
        if (history.isEmpty()) return null
        val timeline = ArrayList<GameState>(history.size + 1).apply {
            addAll(history)
            add(currentState)
        }
        val autoHashes = LongArray(timeline.size) { idx ->
            SolverSuccessorGenerator.autoAdvanceSafeFoundationsWithMoves(timeline[idx], config).first.boardHash()
        }
        val segments = ArrayList<IntRange>()
        var segStart = 0
        for (i in 1 until timeline.size) {
            val sameSegment = autoHashes[i] == autoHashes[i - 1] ||
                GameRules.inferMove(timeline[i - 1], timeline[i]) == SolverMove.Draw
            if (!sameSegment) {
                segments.add(segStart until i)
                segStart = i
            }
        }
        segments.add(segStart until timeline.size)
        if (segments.size <= 1) return null

        var highSeg = segments.lastIndex
        for (segIdx in 0 until segments.lastIndex) {
            if (hasStructuralDeadlock(timeline[segments[segIdx].last])) {
                highSeg = segIdx
                break
            }
        }
        if (highSeg == 0) return null

        fun checkSegmentSolvable(segIdx: Int): Boolean {
            val endIndex = segments[segIdx].last
            val s = timeline[endIndex]
            val hash = autoHashes[endIndex]
            knownSolvability?.get(hash)?.let { return it }
            val solvable = solveOmniscient(
                initial = s,
                maxStates = maxStatesPerCheck,
                maxDurationMs = maxDurationPerCheckMs,
                config = config,
                stopOnFirstSolution = true,
            ).isSolvable
            knownSolvability?.put(hash, solvable)
            return solvable
        }

        if (!checkSegmentSolvable(0)) return null

        var lowSeg = 0
        while (lowSeg + 1 < highSeg) {
            val mid = (lowSeg + highSeg) ushr 1
            if (checkSegmentSolvable(mid)) {
                lowSeg = mid
            } else {
                highSeg = mid
            }
        }

        val k = segments[lowSeg].last
        val solvableState = timeline[k]
        val unsolvableState = timeline[k + 1]
        val fatalMove = GameRules.inferMove(solvableState, unsolvableState) ?: SolverMove.Draw
        val movesToUndo = history.size - k
        return LastSolvablePoint(
            historyIndex = k,
            solvableState = solvableState,
            unsolvableState = unsolvableState,
            fatalMove = fatalMove,
            movesToUndo = movesToUndo,
        )
    }

    private fun minTotalMovesLowerBound(node: SearchNode): Int {
        val remainingFoundations = Card.DECK_SIZE - node.state.foundationCardCount
        val minStockDraws = (node.state.stock.size + node.state.drawCount - 1) / node.state.drawCount
        return node.moveCount + remainingFoundations + minStockDraws
    }

    private fun priorityScore(node: SearchNode, config: SolverConfig): Int =
        heuristicScoreOmniscient(node.state, config) -
            node.depth * DEPTH_COST_WEIGHT -
            node.drawMoveCount * EXTRA_DRAW_COST_WEIGHT -
            node.recycleCount * RECYCLE_COST_WEIGHT

    private fun finishIfSolved(
        node: SearchNode,
        explored: Int,
        config: SolverConfig,
        initial: GameState,
        stopOnFirstSolution: Boolean,
    ): SolverResult? {
        if (node.state.isWon) {
            if (stopOnFirstSolution) {
                return SolverResult(isSolvable = true, exploredStates = explored)
            }
            val raw = reconstructMoves(node)
            val compacted = SolverSolutionCompactor.compactSolution(initial, raw, config)
            return SolverResult(
                isSolvable = true,
                winningMoves = compacted,
                exploredStates = explored,
            )
        }
        if (node.state.areAllTableauCardsFaceUp) {
            if (stopOnFirstSolution) {
                if (AutoCompleter.canAutoComplete(node.state)) {
                    return SolverResult(isSolvable = true, exploredStates = explored)
                }
            } else {
                AutoCompleter.simulateAutoCompleteWithMoves(node.state, config)?.let { (_, autoMoves) ->
                    val raw = reconstructMoves(node) + autoMoves
                    val compacted = SolverSolutionCompactor.compactSolution(initial, raw, config)
                    return SolverResult(
                        isSolvable = true,
                        winningMoves = compacted,
                        exploredStates = explored,
                    )
                }
            }
        }
        return null
    }

    private fun reconstructMoves(goalNode: SearchNode): List<SolverMove> {
        val segments = ArrayDeque<List<SolverMove>>()
        var curr: SearchNode? = goalNode
        while (curr != null) {
            if (curr.edgeMoves.isNotEmpty()) segments.addFirst(curr.edgeMoves)
            curr = curr.parent
        }
        return segments.flatten()
    }

    private fun heuristicScoreOmniscient(s: GameState, config: SolverConfig): Int {
        var score = s.foundationCardCount * config.foundationCardReward
        var hiddenPenalty = 0
        for (col in s.tableau) {
            for (c in col) {
                if (!c.faceUp) {
                    val fSize = GameRules.foundationSize(s, c.suit)
                    val urgency = if (c.rank == fSize + 1) {
                        config.hiddenNextFoundationUrgency
                    } else {
                        (Card.RANKS_PER_SUIT + 1 - c.rank) * config.hiddenLowRankWeight
                    }
                    hiddenPenalty += config.hiddenCardBasePenalty + urgency
                }
            }
            if (col.isEmpty()) score += config.emptyColumnReward
        }
        // Bonus for columns whose first face-up card sits above hidden cards and now has an exposed destination.
        for (col in s.tableau) {
            val firstUp = col.indexOfFirst { it.faceUp }
            if (firstUp > 0) {
                val pivot = col[firstUp]
                val hasParent = if (pivot.isKing) {
                    s.tableau.any { it.isEmpty() }
                } else {
                    s.tableau.any { dest ->
                        dest.isNotEmpty() && GameRules.canPlaceOnTableau(dest, pivot)
                    }
                }
                if (hasParent) score += UNLOCK_READY_BONUS
            }
        }
        score -= hiddenPenalty
        score -= (s.stock.size + s.waste.size) * config.stockWasteCardPenalty
        score -= s.waste.size * WASTE_PILE_EXTRA_PENALTY
        return score
    }

    private const val DEADLINE_CHECK_MASK = 63
    private const val DEADLOCK_MARKER_MOVES = 0
    private const val NANOS_PER_MS = 1_000_000L
    private const val DEPTH_COST_WEIGHT = 2
    private const val EXTRA_DRAW_COST_WEIGHT = 2
    private const val RECYCLE_COST_WEIGHT = 8
    private const val WASTE_PILE_EXTRA_PENALTY = 4
    private const val UNLOCK_READY_BONUS = 20
    private const val GREEDY_BUDGET_DIVISOR = 4
    private const val MIN_GREEDY_STATES = 400
    private const val MAX_GREEDY_STATES = 1_500
    private const val INTERACTIVE_BUDGET_THRESHOLD = 50_000
    private const val DEFAULT_IMPROVEMENT_STATES = 600
    private const val INTERACTIVE_IMPROVEMENT_STATES = 3_500
}
