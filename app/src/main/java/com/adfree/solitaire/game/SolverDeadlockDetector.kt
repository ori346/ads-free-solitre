package com.adfree.solitaire.game

/**
 * Sound structural deadlock detector for Klondike game snapshots.
 */
internal object SolverDeadlockDetector {
    /**
     * Returns `true` only when [s] is provably deadlocked with zero possibility of victory:
     * either no legal move exists anywhere on the board or across the entire stock/waste cycle,
     * or a face-down tableau column is trapped in a closed dependency cycle where its bottom-most
     * face-up card can never legally move to a tableau parent or foundation.
     */
    fun hasStructuralDeadlock(s: GameState): Boolean {
        if (s.isWon) return false
        if (!hasAnyImmediateBoardMove(s) && !HintFinder.canStockCycleProduceMove(s)) {
            return true
        }
        return hasLockedFaceDownColumn(s)
    }

    private fun hasAnyImmediateBoardMove(s: GameState): Boolean {
        if (s.waste.isNotEmpty() && GameRules.canMoveToAnyTarget(s, Pile.Waste, s.waste.lastIndex)) {
            return true
        }
        for (t in GameRules.allTableaus) {
            val col = s.pile(t)
            for (i in col.indices) {
                if (col[i].faceUp && GameRules.canMoveToAnyTarget(s, t, i)) return true
            }
        }
        for (f in GameRules.allFoundations) {
            val fPile = s.pile(f)
            if (fPile.isNotEmpty() && GameRules.canMoveToAnyTarget(s, f, fPile.lastIndex)) {
                return true
            }
        }
        return false
    }

    /**
     * Fixed-point reachability check: starts from all non-hidden cards and iteratively unlocks
     * face-down tableau columns whose bottom-most face-up card can potentially reach a valid parent
     * or complete its foundation chain. Any column remaining locked can never flip in any playout.
     */
    private fun hasLockedFaceDownColumn(s: GameState): Boolean {
        val lockedColumns = GameRules.allTableaus
            .map { s.pile(it) }
            .filter { col -> col.isNotEmpty() && !col.first().faceUp }
        if (lockedColumns.isEmpty()) return false

        val reachableIds = HashSet<Int>(Card.DECK_SIZE)
        val presentIds = HashSet<Int>(Card.DECK_SIZE)
        s.stock.forEach {
            reachableIds.add(it.id)
            presentIds.add(it.id)
        }
        s.waste.forEach {
            reachableIds.add(it.id)
            presentIds.add(it.id)
        }
        s.foundations.flatten().forEach {
            reachableIds.add(it.id)
            presentIds.add(it.id)
        }
        for (col in s.tableau) {
            for (c in col) {
                presentIds.add(c.id)
                if (c.faceUp) reachableIds.add(c.id)
            }
        }

        val unlocked = BooleanArray(lockedColumns.size)
        var changed = true
        while (changed) {
            changed = false
            for (i in lockedColumns.indices) {
                if (unlocked[i]) continue
                val col = lockedColumns[i]
                val firstUp = col.indexOfFirst { it.faceUp }
                if (firstUp < 0) continue
                val pivot = col[firstUp]
                if (canPivotPotentiallyMove(s, pivot, reachableIds, presentIds)) {
                    unlocked[i] = true
                    changed = true
                    for (j in 0 until firstUp) reachableIds.add(col[j].id)
                }
            }
        }
        return unlocked.any { !it }
    }

    private fun canPivotPotentiallyMove(
        s: GameState,
        pivot: Card,
        reachableIds: Set<Int>,
        presentIds: Set<Int>,
    ): Boolean {
        if (pivot.isKing) return true
        val parentRank = pivot.rank + 1
        val hasTableauParent = Suit.entries
            .filter { it.isRed != pivot.suit.isRed }
            .any { parentSuit -> Card(parentSuit, parentRank).id in reachableIds }
        if (hasTableauParent) return true

        val fSize = GameRules.foundationSize(s, pivot.suit)
        return (fSize + 1 until pivot.rank).all { rank ->
            val id = Card(pivot.suit, rank).id
            id in presentIds && id in reachableIds
        }
    }
}
