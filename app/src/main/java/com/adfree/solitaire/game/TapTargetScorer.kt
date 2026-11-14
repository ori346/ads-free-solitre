package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.TapHeuristicConfig

/**
 * Scores and ranks legal tap destinations to choose the smartest move for a tapped card or stack.
 */
object TapTargetScorer {
    private const val IMMEDIATE_FOUNDATION_GAP = 1
    private const val NEXT_FOUNDATION_GAP = 2

    /**
     * Scores a legal tableau destination [to] when moving cards from [from] starting at [index].
     * Higher scores indicate better strategic placements.
     */
    fun scoreTableauTarget(
        s: GameState,
        from: Pile,
        index: Int,
        to: Pile.Tableau,
        config: TapHeuristicConfig = DefaultGameConfig.tapHeuristics,
    ): Int {
        val src = s.pile(from)
        val destCol = s.pile(to)
        if (destCol.isEmpty()) {
            return if (from is Pile.Tableau && index == 0) Int.MIN_VALUE else config.emptyColumnKingScore
        }

        val targetTop = destCol.last()
        var score = config.baseTableauScore

        // Penalize lateral parent swaps unless the uncovered face-up card can immediately promote to foundation.
        if (from is Pile.Tableau &&
            index > 0 &&
            src[index - 1].faceUp &&
            GameRules.isValidRun(listOf(src[index - 1], src[index]))
        ) {
            if (!GameRules.canMoveCardToAnyFoundation(s, src[index - 1])) {
                score -= config.lateralParentSwapPenalty
            }
        }

        score += foundationGapScoreDelta(s, targetTop, config)

        val buriesBlockedFoundationCard = (0 until destCol.lastIndex).any { idx ->
            val card = destCol[idx]
            card.faceUp && GameRules.canMoveCardToAnyFoundation(s, card)
        }
        if (buriesBlockedFoundationCard) {
            score -= config.buryBlockingFoundationCardPenalty
        }

        val firstFaceUpIdx = destCol.indexOfFirst { it.faceUp }
        val hiddenCount = if (firstFaceUpIdx >= 0) firstFaceUpIdx else destCol.size
        val faceUpRunSize = destCol.size - hiddenCount
        val isAnchoredRun = faceUpRunSize >= 2 || targetTop.isKing

        score -= hiddenCount * config.hiddenCardPenalty
        score += if (isAnchoredRun) {
            config.establishedRunBonus
        } else {
            -config.unanchoredSingleCardPenalty
        }
        score -= faceUpRunSize * config.faceUpRunCardPenalty
        score -= destCol.size * config.columnTotalCardPenalty
        return score
    }

    private fun foundationGapScoreDelta(
        s: GameState,
        targetTop: Card,
        config: TapHeuristicConfig,
    ): Int {
        val fSize = GameRules.foundationSize(s, targetTop.suit)
        val gap = targetTop.rank - fSize
        if (gap <= IMMEDIATE_FOUNDATION_GAP) return -config.buryImmediateFoundationPenalty
        if (gap == NEXT_FOUNDATION_GAP) {
            val nextNeededRank = fSize + 1
            val nextNeededExposed = isCardExposedOnTop(s, targetTop.suit, nextNeededRank)
            return if (nextNeededExposed) {
                -config.buryNextFoundationExposedPenalty
            } else {
                -config.buryNextFoundationUnexposedPenalty
            }
        }
        return minOf(gap, config.maxFoundationGapBonus) * config.foundationGapBonusMultiplier
    }

    private fun isCardExposedOnTop(s: GameState, suit: Suit, rank: Int): Boolean {
        val wasteMatch = s.waste.lastOrNull()?.let { it.suit == suit && it.rank == rank } == true
        if (wasteMatch) return true
        return s.tableau.any { col ->
            col.lastOrNull()?.let { it.suit == suit && it.rank == rank } == true
        }
    }

    /** Returns all legal destination piles for [from] starting at [index], ordered best to worst. */
    fun rankedTargets(
        s: GameState,
        from: Pile,
        index: Int,
        config: TapHeuristicConfig = DefaultGameConfig.tapHeuristics,
    ): List<Pile> {
        val src = s.pile(from)
        if (index !in src.indices) return emptyList()
        val foundations = if (index == src.lastIndex && from !is Pile.Foundation) {
            GameRules.allFoundations.filter { GameRules.canMove(s, from, index, it) }
        } else {
            emptyList()
        }
        val wholeColumnKing = from is Pile.Tableau && index == 0
        val tableaus = GameRules.allTableaus
            .filter { GameRules.canMove(s, from, index, it) && !(wholeColumnKing && s.pile(it).isEmpty()) }
            .sortedByDescending { scoreTableauTarget(s, from, index, it, config) }
        return foundations + tableaus
    }

    /** Returns the highest-ranked legal destination, preferring targets outside [avoidTargets]. */
    fun bestTarget(
        s: GameState,
        from: Pile,
        index: Int,
        avoidTargets: Set<Pile> = emptySet(),
        config: TapHeuristicConfig = DefaultGameConfig.tapHeuristics,
    ): Pile? {
        val candidates = rankedTargets(s, from, index, config)
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it !in avoidTargets } ?: candidates.first()
    }

    /**
     * True when moving the stack starting at [k] from tableau [from] to [target] makes
     * structural progress (reveals a hidden card, empties a column, or frees a foundation card).
     */
    fun isProgressTableauMove(s: GameState, from: Pile.Tableau, k: Int, target: Pile): Boolean {
        val src = s.pile(from)
        if (k !in src.indices) return false
        if (target is Pile.Foundation) return true
        if (k == 0) return s.pile(target).isNotEmpty()
        val prev = src[k - 1]
        if (!prev.faceUp) return true
        if (!GameRules.isValidRun(listOf(prev, src[k]))) return true
        return GameRules.canMoveCardToAnyFoundation(s, prev)
    }

    /**
     * Resolves the best `(resolvedStartIndex, targetPile)` for a tap on [from] at [index],
     * avoiding piles in [avoidTargets] before wrapping around when all destinations were visited.
     */
    fun bestTapMove(
        s: GameState,
        from: Pile,
        index: Int,
        avoidTargets: Set<Pile> = emptySet(),
        config: TapHeuristicConfig = DefaultGameConfig.tapHeuristics,
    ): Pair<Int, Pile>? {
        val src = s.pile(from)
        if (index !in src.indices || !src[index].faceUp) return null
        resolveTapWithAvoided(s, src, from, index, avoidTargets, config)?.let { return it }
        if (avoidTargets.isNotEmpty()) {
            return resolveTapWithAvoided(s, src, from, index, emptySet(), config)
        }
        return null
    }

    private fun firstNonAvoidedTarget(
        s: GameState,
        from: Pile,
        index: Int,
        avoidTargets: Set<Pile>,
        config: TapHeuristicConfig,
    ): Pile? = rankedTargets(s, from, index, config).firstOrNull { it !in avoidTargets }

    private fun resolveTapWithAvoided(
        s: GameState,
        src: List<Card>,
        from: Pile,
        index: Int,
        avoidTargets: Set<Pile>,
        config: TapHeuristicConfig,
    ): Pair<Int, Pile>? {
        // 1. Honor explicit stack tap when moving that stack makes structural progress.
        if (from is Pile.Tableau && index < src.lastIndex && GameRules.isValidRun(src.subList(index, src.size))) {
            val target = firstNonAvoidedTarget(s, from, index, avoidTargets, config)
            if (target != null && isProgressTableauMove(s, from, index, target)) {
                return index to target
            }
        }

        // 2. Exposed top card to an unvisited Foundation.
        if (from !is Pile.Foundation) {
            val topIdx = src.lastIndex
            GameRules.allFoundations
                .firstOrNull { GameRules.canMove(s, from, topIdx, it) && it !in avoidTargets }
                ?.let { return topIdx to it }
        }

        val firstUp = if (from is Pile.Tableau) src.indexOfFirst { it.faceUp }.coerceAtLeast(0) else index

        // 3. Deepest valid run root in the column that makes structural progress.
        if (from is Pile.Tableau) {
            for (k in firstUp..src.lastIndex) {
                if (!GameRules.isValidRun(src.subList(k, src.size))) continue
                val target = firstNonAvoidedTarget(s, from, k, avoidTargets, config)
                if (target != null && isProgressTableauMove(s, from, k, target)) {
                    return k to target
                }
            }
        }

        // 4. Direct move from the tapped card index.
        firstNonAvoidedTarget(s, from, index, avoidTargets, config)?.let { return index to it }

        if (from is Pile.Tableau) {
            // 5. Higher cards in the same valid face-up run.
            for (k in (index - 1) downTo firstUp) {
                if (!GameRules.isValidRun(src.subList(k, src.size))) break
                firstNonAvoidedTarget(s, from, k, avoidTargets, config)?.let { return k to it }
            }
            // 6. Lower cards down to the exposed top card.
            for (k in src.lastIndex downTo (index + 1)) {
                if (!src[k].faceUp) break
                firstNonAvoidedTarget(s, from, k, avoidTargets, config)?.let { return k to it }
            }
        }
        return null
    }

    /** Resolves the highest valid draggable run start index containing [index] when dragging from [pile]. */
    fun resolveDraggableStackStartIndex(s: GameState, pile: Pile, index: Int): Int {
        if (pile !is Pile.Tableau) return index
        val src = s.pile(pile)
        if (index !in src.indices || !src[index].faceUp) return index
        if (GameRules.canMoveToAnyTarget(s, pile, index)) return index
        for (k in (index - 1) downTo 0) {
            if (!src[k].faceUp) break
            if (!GameRules.isValidRun(src.subList(k, src.size))) break
            if (GameRules.canMoveToAnyTarget(s, pile, k)) return k
        }
        return index
    }
}
