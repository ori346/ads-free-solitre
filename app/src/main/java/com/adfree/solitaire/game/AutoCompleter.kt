package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.SolverConfig

/**
 * Deterministic auto-completion checker and step executor once all tableau cards are face-up.
 */
object AutoCompleter {
    /**
     * True when the game is not yet won, every tableau card is face-up, and simulating
     * auto-completion to victory succeeds cleanly in the current draw mode.
     */
    fun canAutoComplete(s: GameState): Boolean {
        if (s.isWon || !s.areAllTableauCardsFaceUp) return false
        return simulateAutoComplete(s) != null
    }

    /**
     * Maximum consecutive auto-complete steps without foundation progress before aborting.
     * Sized to exceed a full stock/waste cycle plus interleaved tableau-splitting moves.
     */
    fun autoCompleteStallLimit(
        s: GameState,
        config: SolverConfig = DefaultGameConfig.solver,
    ): Int = config.autoCompleteStallMultiplier * (s.stock.size + s.waste.size) + config.autoCompleteStallBaseMargin

    /** Runs auto-complete to completion and returns the won [GameState], or `null` if it stalls. */
    fun simulateAutoComplete(
        s: GameState,
        config: SolverConfig = DefaultGameConfig.solver,
    ): GameState? = simulateAutoCompleteWithMoves(s, config)?.first

    private enum class WasteParkPolicy {
        NONE,
        UNCOVER_FOUNDATION,
        WHEN_PREDECESSOR_IN_DECK,
        ALWAYS,
    }

    /**
     * Runs auto-complete to completion and returns `(wonState, executedMoves)`, or `null` if it stalls.
     */
    fun simulateAutoCompleteWithMoves(
        s: GameState,
        config: SolverConfig = DefaultGameConfig.solver,
    ): Pair<GameState, List<SolverMove>>? =
        WasteParkPolicy.entries
            .mapNotNull { policy -> runSimulation(s, config, policy) }
            .minByOrNull { it.second.size }

    private fun runSimulation(
        s: GameState,
        config: SolverConfig,
        policy: WasteParkPolicy,
    ): Pair<GameState, List<SolverMove>>? {
        var curr = s
        var found = curr.foundationCardCount
        var stalled = 0
        val moves = mutableListOf<SolverMove>()
        while (!curr.isWon) {
            val (move, next) = autoCompleteStepWithMove(curr, policy) ?: return null
            moves.add(move)
            curr = next
            val f = curr.foundationCardCount
            if (f > found) {
                found = f
                stalled = 0
            } else if (++stalled > autoCompleteStallLimit(curr, config)) {
                return null
            }
        }
        return curr to moves
    }

    /** Executes one auto-complete step, returning the resulting [GameState] or `null` if stuck. */
    fun autoCompleteStep(s: GameState): GameState? = autoCompleteStepWithMove(s)?.second

    /** Executes one auto-complete step and returns both the [SolverMove] taken and the next [GameState]. */
    fun autoCompleteStepWithMove(s: GameState): Pair<SolverMove, GameState>? =
        autoCompleteStepWithMove(s, WasteParkPolicy.WHEN_PREDECESSOR_IN_DECK)

    private fun autoCompleteStepWithMove(
        s: GameState,
        policy: WasteParkPolicy,
    ): Pair<SolverMove, GameState>? {
        // 1. Move the lowest-rank playable exposed card to a foundation (prefer Waste on rank ties).
        val topSources = GameRules.rankOrderedExposedSourcePiles(s)
        for (p in topSources) {
            val idx = s.pile(p).lastIndex
            for (f in GameRules.allFoundations) {
                GameRules.move(s, p, idx, f)?.let { return SolverMove.Move(p, idx, f) to it }
            }
        }

        // 2. Uncover a card stuck beneath a non-descending tableau boundary.
        for (t in GameRules.allTableaus) {
            val col = s.pile(t)
            for (i in col.indices) {
                if (i == 0) continue
                val prev = col[i - 1]
                val curr = col[i]
                val isDescendingPair = prev.rank == curr.rank + 1 && prev.hasOppositeColor(curr)
                if (!isDescendingPair) {
                    for (dest in GameRules.allTableaus) {
                        if (dest == t) continue
                        GameRules.move(s, t, i, dest)?.let { return SolverMove.Move(t, i, dest) to it }
                    }
                }
            }
        }

        // 3. Optionally park the top waste card onto a clean descending tableau column before drawing over it.
        if (shouldParkWasteCard(s, policy)) {
            val wIdx = s.waste.lastIndex
            for (dest in GameRules.allTableaus) {
                val destCol = s.pile(dest)
                if (destCol.isEmpty() || !GameRules.isValidRun(destCol)) continue
                GameRules.move(s, Pile.Waste, wIdx, dest)?.let {
                    return SolverMove.Move(Pile.Waste, wIdx, dest) to it
                }
            }
        }

        // 4. Draw or recycle stock/waste without penalty.
        if (s.stock.isNotEmpty() || s.waste.isNotEmpty()) {
            GameRules.draw(s, applyPenalty = false)?.let { return SolverMove.Draw to it }
        }
        return null
    }

    private fun shouldParkWasteCard(s: GameState, policy: WasteParkPolicy): Boolean {
        if (s.waste.isEmpty()) return false
        return when (policy) {
            WasteParkPolicy.NONE -> false
            WasteParkPolicy.UNCOVER_FOUNDATION -> {
                s.waste.size >= 2 && GameRules.canMoveCardToAnyFoundation(s, s.waste[s.waste.lastIndex - 1])
            }
            WasteParkPolicy.WHEN_PREDECESSOR_IN_DECK -> {
                val top = s.waste.last()
                val uncoversFoundation =
                    s.waste.size >= 2 && GameRules.canMoveCardToAnyFoundation(s, s.waste[s.waste.lastIndex - 1])
                if (uncoversFoundation) return true
                if (s.stock.isEmpty() || top.rank <= Card.RANK_ACE) return false
                val neededId = Card(top.suit, top.rank - 1).id
                s.stock.any { it.id == neededId } || s.waste.any { it.id == neededId }
            }
            WasteParkPolicy.ALWAYS -> s.stock.isNotEmpty() || s.waste.size > 1
        }
    }
}
