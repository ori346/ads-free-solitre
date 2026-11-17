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
     * Fixed-point reachability check using 64-bit card bitmasks and backward-induction stack
     * clearability: starts from all non-hidden cards and iteratively unlocks face-down tableau
     * columns whose face-up stack can potentially clear off the column. Any column remaining
     * locked—or any fully face-up non-descending column that cannot clear—is provably deadlocked.
     */
    private fun hasLockedFaceDownColumn(s: GameState): Boolean {
        var presentMask = 0L
        var reachableMask = 0L
        for (c in s.stock) {
            val bit = 1L shl c.id
            presentMask = presentMask or bit
            reachableMask = reachableMask or bit
        }
        for (c in s.waste) {
            val bit = 1L shl c.id
            presentMask = presentMask or bit
            reachableMask = reachableMask or bit
        }
        for (f in s.foundations) {
            for (c in f) {
                val bit = 1L shl c.id
                presentMask = presentMask or bit
                reachableMask = reachableMask or bit
            }
        }

        var lockedColMask = 0
        var faceUpColMask = 0
        var hasEmptyColumn = false
        val tableau = s.tableau
        for (colIdx in tableau.indices) {
            val col = tableau[colIdx]
            if (col.isEmpty()) {
                hasEmptyColumn = true
                continue
            }
            val colBit = 1 shl colIdx
            if (col[0].faceUp) {
                faceUpColMask = faceUpColMask or colBit
            } else {
                lockedColMask = lockedColMask or colBit
            }
            for (c in col) {
                val bit = 1L shl c.id
                presentMask = presentMask or bit
                if (c.faceUp) reachableMask = reachableMask or bit
            }
        }

        if (lockedColMask != 0) {
            var unlockedColMask = 0
            var canHaveEmpty = hasEmptyColumn
            var changed = true
            while (changed) {
                changed = false
                if (!canHaveEmpty) {
                    for (colIdx in tableau.indices) {
                        if ((faceUpColMask and (1 shl colIdx)) != 0 &&
                            canClearFaceUpFrom(s, tableau[colIdx], 0, reachableMask, presentMask, canHaveEmptyColumn = false)
                        ) {
                            canHaveEmpty = true
                            break
                        }
                    }
                }
                for (colIdx in tableau.indices) {
                    val colBit = 1 shl colIdx
                    if ((lockedColMask and colBit) == 0 || (unlockedColMask and colBit) != 0) continue
                    val col = tableau[colIdx]
                    val firstUp = col.indexOfFirst { it.faceUp }
                    if (firstUp < 0) continue
                    if (canClearFaceUpFrom(s, col, firstUp, reachableMask, presentMask, canHaveEmpty)) {
                        unlockedColMask = unlockedColMask or colBit
                        canHaveEmpty = true
                        changed = true
                        for (j in 0 until firstUp) {
                            reachableMask = reachableMask or (1L shl col[j].id)
                        }
                    }
                }
            }
            if (unlockedColMask != lockedColMask) return true
        }

        for (colIdx in tableau.indices) {
            if ((faceUpColMask and (1 shl colIdx)) != 0) {
                val col = tableau[colIdx]
                if (!GameRules.isValidRun(col) &&
                    !canClearFaceUpFrom(s, col, 0, reachableMask, presentMask, canHaveEmptyColumn = true)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun canClearFaceUpFrom(
        s: GameState,
        col: List<Card>,
        startIdx: Int,
        reachableMask: Long,
        presentMask: Long,
        canHaveEmptyColumn: Boolean,
    ): Boolean {
        var canClearAbove = true
        var canFormValidSuffix = true
        for (k in col.lastIndex downTo startIdx) {
            val card = col[k]
            if (k < col.lastIndex) {
                val above = col[k + 1]
                val validPair = card.hasOppositeColor(above) && above.rank == card.rank - 1
                canFormValidSuffix = canClearAbove || (validPair && canFormValidSuffix)
            }
            val excludeParentId = if (k > startIdx) col[k - 1].id else -1
            val hasTableauDest = if (card.isKing) {
                canHaveEmptyColumn
            } else {
                hasReachableTableauParent(card, excludeParentId, reachableMask)
            }
            val canPromote = canClearAbove && canReachFoundation(s, card, reachableMask, presentMask)
            canClearAbove = (canFormValidSuffix && hasTableauDest) || canPromote
        }
        return canClearAbove
    }

    private fun hasReachableTableauParent(
        card: Card,
        excludeParentId: Int,
        reachableMask: Long,
    ): Boolean {
        val parentRank = card.rank + 1
        if (parentRank > Card.RANK_KING) return false
        val oppRed = !card.suit.isRed
        for (suit in Suit.entries) {
            if (suit.isRed == oppRed) {
                val parentId = suit.ordinal * Card.RANKS_PER_SUIT + parentRank - 1
                if (parentId != excludeParentId && ((reachableMask ushr parentId) and 1L) != 0L) {
                    return true
                }
            }
        }
        return false
    }

    private fun canReachFoundation(
        s: GameState,
        card: Card,
        reachableMask: Long,
        presentMask: Long,
    ): Boolean {
        val fSize = GameRules.foundationSize(s, card.suit)
        if (card.rank <= fSize + 1) return true
        val suitOffset = card.suit.ordinal * Card.RANKS_PER_SUIT - 1
        for (rank in (fSize + 1) until card.rank) {
            val bit = 1L shl (suitOffset + rank)
            if ((presentMask and bit) == 0L || (reachableMask and bit) == 0L) {
                return false
            }
        }
        return true
    }
}
