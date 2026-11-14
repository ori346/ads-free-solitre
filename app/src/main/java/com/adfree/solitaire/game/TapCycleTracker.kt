package com.adfree.solitaire.game

/**
 * Tracks consecutive taps on the same card or stack so repeated taps cycle through every legal
 * destination without skipping any alternative before wrapping around.
 */
class TapCycleTracker {
    private var lastTapCardIds: Set<Int> = emptySet()
    private val visitedDestinations = LinkedHashSet<Pile>()
    private var lastSourcePile: Pile? = null

    /**
     * Resolves the best `(resolvedStartIndex, targetPile)` for a tap on [pile] at [index] in [state],
     * avoiding destinations already visited in the current cycle for the same card group.
     */
    fun resolveTapMove(state: GameState, pile: Pile, index: Int): Pair<Int, Pile>? {
        val src = state.pile(pile)
        if (index !in src.indices) return null
        val isSameTappedGroup = lastTapCardIds.isNotEmpty() && src[index].id in lastTapCardIds
        val avoid: Set<Pile> = if (isSameTappedGroup) {
            lastSourcePile?.let { visitedDestinations + it } ?: visitedDestinations
        } else {
            emptySet()
        }
        val resolved = TapTargetScorer.bestTapMove(state, pile, index, avoid) ?: return null
        if (isSameTappedGroup) {
            val movedIds = src.drop(resolved.first).map { it.id }.toSet()
            if (movedIds != lastTapCardIds) {
                return TapTargetScorer.bestTapMove(state, pile, index)
            }
        }
        return resolved
    }

    /**
     * Records a completed tap move from [from] (starting at [resolvedIndex]) to [to] based on
     * [stateBeforeMove] so subsequent taps on the same card group advance the destination cycle.
     */
    fun recordMove(stateBeforeMove: GameState, from: Pile, resolvedIndex: Int, to: Pile) {
        val src = stateBeforeMove.pile(from)
        if (resolvedIndex !in src.indices) {
            clear()
            return
        }
        val movedIds = src.drop(resolvedIndex).map { it.id }.toSet()
        val allTargets = TapTargetScorer.rankedTargets(stateBeforeMove, from, resolvedIndex)
        val hasUnvisitedDestination = movedIds == lastTapCardIds &&
            allTargets.any { it !in visitedDestinations }
        if (!hasUnvisitedDestination) {
            visitedDestinations.clear()
        }
        lastTapCardIds = movedIds
        lastSourcePile = from
        visitedDestinations.add(to)
    }

    /** Clears all tracked tap-cycle history. */
    fun clear() {
        lastTapCardIds = emptySet()
        visitedDestinations.clear()
        lastSourcePile = null
    }
}
