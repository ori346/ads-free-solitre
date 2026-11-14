package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.GameConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Klondike Solitaire movement, scoring, and stock-cycle rules.
 */
object GameRules {
    /** All four foundation pile descriptors. */
    val allFoundations: List<Pile.Foundation> =
        (0 until DefaultGameConfig.rules.suitsCount).map { Pile.Foundation(it) }

    /** All seven tableau column pile descriptors. */
    val allTableaus: List<Pile.Tableau> =
        (0 until DefaultGameConfig.rules.tableauColumnCount).map { Pile.Tableau(it) }

    /**
     * Draws up to `drawCount` cards from stock to waste, or recycles waste into stock when empty.
     * Returns `null` if both stock and waste are empty.
     */
    fun draw(
        s: GameState,
        applyPenalty: Boolean = true,
        config: GameConfig = DefaultGameConfig,
    ): GameState? {
        if (s.stock.isEmpty()) {
            if (s.waste.isEmpty()) return null
            val penalty = when {
                !applyPenalty -> 0
                s.drawCount == DRAW_ONE_COUNT -> config.scoring.recyclePenaltyDraw1
                else -> config.scoring.recyclePenaltyDraw3
            }
            return s.copy(
                stock = s.waste.reversed().map { it.copy(faceUp = false) },
                waste = emptyList(),
                score = max(config.scoring.minScore, s.score - penalty),
                moves = s.moves + 1,
            )
        }
        val n = min(s.drawCount, s.stock.size)
        val drawn = s.stock.takeLast(n).reversed().map { it.copy(faceUp = true) }
        return s.copy(
            stock = s.stock.dropLast(n),
            waste = s.waste + drawn,
            moves = s.moves + 1,
        )
    }

    /** True if card [c] can legally be placed on top of foundation pile [f]. */
    fun canPlaceOnFoundation(f: List<Card>, c: Card): Boolean =
        if (f.isEmpty()) c.isAce else f.last().suit == c.suit && c.rank == f.last().rank + 1

    /**
     * True if card [c] can legally be placed onto foundation [f] in state [s], preferring each
     * suit's canonical slot while allowing fallback empty slots if the preferred slot is occupied.
     */
    fun canPlaceOnFoundation(s: GameState, f: Pile.Foundation, c: Card): Boolean {
        val pile = s.pile(f)
        if (pile.isNotEmpty()) {
            return pile.last().suit == c.suit && c.rank == pile.last().rank + 1
        }
        if (!c.isAce) return false
        val preferred = Suit.entries.getOrNull(f.i)
        if (preferred == c.suit) return true
        val preferredPile = s.foundations.getOrNull(c.suit.ordinal)
        return preferredPile != null && preferredPile.isNotEmpty()
    }

    /** Returns the number of cards currently on the foundation holding [suit]. */
    fun foundationSize(s: GameState, suit: Suit): Int =
        s.foundations.firstOrNull { it.firstOrNull()?.suit == suit }?.size ?: 0

    /** Returns the first foundation pile in [s] that can legally receive [c], or `null`. */
    fun foundationTargetFor(s: GameState, c: Card): Pile.Foundation? =
        allFoundations.firstOrNull { canPlaceOnFoundation(s, it, c) }

    /** True if [c] can immediately be placed onto any foundation pile in [s]. */
    fun canMoveCardToAnyFoundation(s: GameState, c: Card): Boolean =
        foundationTargetFor(s, c) != null

    /**
     * Returns non-empty waste and tableau piles in [s] ordered by lowest exposed top-card rank first,
     * preferring [Pile.Waste] over tableau columns on rank ties.
     */
    fun rankOrderedExposedSourcePiles(s: GameState): List<Pile> = buildList {
        if (s.waste.isNotEmpty()) add(Pile.Waste)
        allTableaus.forEach { if (s.pile(it).isNotEmpty()) add(it) }
    }.sortedWith(
        compareBy<Pile> { s.pile(it).last().rank }
            .thenBy { if (it == Pile.Waste) 0 else 1 },
    )

    /** True if card [c] can legally be placed at the bottom of tableau column [t]. */
    fun canPlaceOnTableau(t: List<Card>, c: Card): Boolean =
        if (t.isEmpty()) {
            c.isKing
        } else {
            t.last().faceUp && t.last().hasOppositeColor(c) && c.rank == t.last().rank - 1
        }

    /** True when [cards] are all face-up and form a descending alternating-color run. */
    fun isValidRun(cards: List<Card>): Boolean =
        cards.all { it.faceUp } && cards.zipWithNext().all { (a, b) ->
            a.hasOppositeColor(b) && b.rank == a.rank - 1
        }

    /** True if moving cards from [from] starting at [index] onto [to] is legal in [s]. */
    fun canMove(s: GameState, from: Pile, index: Int, to: Pile): Boolean {
        if (from == to || from == Pile.Stock) return false
        val src = s.pile(from)
        if (index !in src.indices) return false
        val cards = src.subList(index, src.size)
        if (from !is Pile.Tableau && cards.size != 1) return false
        if (!isValidRun(cards)) return false
        return when (to) {
            is Pile.Foundation -> cards.size == 1 && canPlaceOnFoundation(s, to, cards[0])
            is Pile.Tableau -> canPlaceOnTableau(s.pile(to), cards[0])
            else -> false
        }
    }

    /**
     * Executes a legal move from [from] at [index] onto [to] with scoring from [config],
     * or returns `null` if the move is illegal.
     */
    fun move(
        s: GameState,
        from: Pile,
        index: Int,
        to: Pile,
        config: GameConfig = DefaultGameConfig,
    ): GameState? {
        if (!canMove(s, from, index, to)) return null
        val src = s.pile(from)
        val cards = src.subList(index, src.size)
        var remaining = src.take(index)
        var delta = when {
            to is Pile.Foundation && from !is Pile.Foundation -> config.scoring.toFoundationPoints
            from == Pile.Waste && to is Pile.Tableau -> config.scoring.wasteToTableauPoints
            from is Pile.Foundation && to is Pile.Tableau -> config.scoring.fromFoundationPenalty
            else -> 0
        }
        if (from is Pile.Tableau && remaining.isNotEmpty() && !remaining.last().faceUp) {
            remaining = remaining.dropLast(1) + remaining.last().copy(faceUp = true)
            delta += config.scoring.flipTableauCardPoints
        }
        return s.withPile(from, remaining)
            .withPile(to, s.pile(to) + cards)
            .copy(score = max(config.scoring.minScore, s.score + delta), moves = s.moves + 1)
    }

    /** True if the card/stack at [from] starting at [index] has any legal foundation or tableau destination. */
    fun canMoveToAnyTarget(s: GameState, from: Pile, index: Int): Boolean =
        allFoundations.any { canMove(s, from, index, it) } ||
            allTableaus.any { canMove(s, from, index, it) }

    /**
     * Yields `(drawSteps, state)` pairs across one complete non-repeating stock/waste cycle,
     * starting with `(0, s)` when either stock or waste is non-empty.
     */
    fun stockWasteCycleStates(s: GameState): Sequence<Pair<Int, GameState>> = sequence {
        if (s.stock.isEmpty() && s.waste.isEmpty()) return@sequence
        val visited = HashSet<Long>()
        var curr = s
        visited.add(curr.stockWasteHash())
        yield(0 to curr)
        var draws = 0
        while (true) {
            val next = draw(curr, applyPenalty = false) ?: break
            if (!visited.add(next.stockWasteHash())) break
            draws++
            curr = next
            yield(draws to curr)
        }
    }

    private const val DRAW_ONE_COUNT = 1
}
