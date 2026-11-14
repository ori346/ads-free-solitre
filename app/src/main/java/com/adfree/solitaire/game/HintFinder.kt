package com.adfree.solitaire.game

/**
 * Computes prioritized player hints and checks whether cycling the stock can yield a playable card.
 */
object HintFinder {
    /** Returns all distinct suggested hints in [s], ordered from highest to lowest priority. */
    fun findHints(s: GameState): List<Hint> {
        val hints = mutableListOf<Hint>()
        val seenSources = HashSet<Pair<Pile, Int>>()

        fun addHint(h: Hint) {
            if (seenSources.add(h.from to h.index)) {
                hints.add(h)
            }
        }

        val sources = collectFaceUpSources(s)
        addFoundationHints(s, sources, ::addHint)
        addProgressTableauHints(s, sources, ::addHint)
        addWasteToTableauHint(s, ::addHint)
        addPartialTableauHints(s, sources, ::addHint)

        if (canStockCycleProduceMove(s)) {
            addHint(Hint(Pile.Stock, 0, Pile.Waste))
        }
        return hints
    }

    /** Returns the highest-priority hint for [s], or `null` if no moves remain. */
    fun findHint(s: GameState): Hint? = findHints(s).firstOrNull()

    /** True if drawing/recycling through the stock and waste cycle can expose a playable waste card. */
    fun canStockCycleProduceMove(s: GameState): Boolean =
        GameRules.stockWasteCycleStates(s)
            .drop(1)
            .any { (_, next) ->
                next.waste.isNotEmpty() && GameRules.canMoveToAnyTarget(next, Pile.Waste, next.waste.lastIndex)
            }

    private fun collectFaceUpSources(s: GameState): List<Pair<Pile, Int>> = buildList {
        if (s.waste.isNotEmpty()) add(Pile.Waste to s.waste.lastIndex)
        for (t in GameRules.allTableaus) {
            val col = s.pile(t)
            for (i in col.indices) {
                if (col[i].faceUp) add(t to i)
            }
        }
    }

    private inline fun addFoundationHints(
        s: GameState,
        sources: List<Pair<Pile, Int>>,
        emit: (Hint) -> Unit,
    ) {
        for ((pile, index) in sources) {
            if (index == s.pile(pile).lastIndex) {
                GameRules.allFoundations
                    .firstOrNull { GameRules.canMove(s, pile, index, it) }
                    ?.let { emit(Hint(pile, index, it)) }
            }
        }
    }

    private inline fun addProgressTableauHints(
        s: GameState,
        sources: List<Pair<Pile, Int>>,
        emit: (Hint) -> Unit,
    ) {
        for ((pile, index) in sources) {
            if (pile !is Pile.Tableau) continue
            val src = s.pile(pile)
            val reveals = index > 0 && !src[index - 1].faceUp
            val targets = GameRules.allTableaus
                .filter { GameRules.canMove(s, pile, index, it) }
                .sortedByDescending { TapTargetScorer.scoreTableauTarget(s, pile, index, it) }
            for (target in targets) {
                val emptiesColumn = index == 0 && s.pile(target).isNotEmpty()
                if (reveals || emptiesColumn) {
                    emit(Hint(pile, index, target))
                    break
                }
            }
        }
    }

    private inline fun addWasteToTableauHint(s: GameState, emit: (Hint) -> Unit) {
        if (s.waste.isEmpty()) return
        val wasteTopIdx = s.waste.lastIndex
        GameRules.allTableaus
            .filter { GameRules.canMove(s, Pile.Waste, wasteTopIdx, it) }
            .maxByOrNull { TapTargetScorer.scoreTableauTarget(s, Pile.Waste, wasteTopIdx, it) }
            ?.let { emit(Hint(Pile.Waste, wasteTopIdx, it)) }
    }

    private inline fun addPartialTableauHints(
        s: GameState,
        sources: List<Pair<Pile, Int>>,
        emit: (Hint) -> Unit,
    ) {
        for ((pile, index) in sources) {
            if (pile !is Pile.Tableau || index == 0) continue
            val prev = s.pile(pile)[index - 1]
            val target = GameRules.allTableaus
                .filter { t ->
                    GameRules.canMove(s, pile, index, t) &&
                        s.pile(t).isNotEmpty() &&
                        s.pile(t).last().id != prev.id
                }
                .maxByOrNull { TapTargetScorer.scoreTableauTarget(s, pile, index, it) }
            if (target != null) {
                emit(Hint(pile, index, target))
            }
        }
    }
}
