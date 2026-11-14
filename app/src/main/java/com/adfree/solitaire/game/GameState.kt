package com.adfree.solitaire.game

/**
 * Immutable Klondike game snapshot. The last element of every card list is the top card.
 */
data class GameState(
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<Card>>,
    val drawCount: Int,
    val score: Int = 0,
    val moves: Int = 0,
) {
    /** True when all four foundation piles contain all 13 ranks. */
    val isWon: Boolean get() = foundations.all { it.size == Card.RANKS_PER_SUIT }

    /** True when every card in every tableau column is face-up. */
    val areAllTableauCardsFaceUp: Boolean get() = tableau.all { col -> col.all { it.faceUp } }

    /** Total number of face-down cards remaining across all tableau columns. */
    val hiddenTableauCardCount: Int get() = tableau.sumOf { col -> col.count { !it.faceUp } }

    /** Total number of cards currently placed on foundation piles. */
    val foundationCardCount: Int get() = foundations.sumOf { it.size }

    /** Returns the card list belonging to pile [p]. */
    fun pile(p: Pile): List<Card> = when (p) {
        Pile.Stock -> stock
        Pile.Waste -> waste
        is Pile.Foundation -> foundations[p.i]
        is Pile.Tableau -> tableau[p.i]
    }

    /** Returns a copy of this state with pile [p] replaced by [cards]. */
    fun withPile(p: Pile, cards: List<Card>): GameState = when (p) {
        Pile.Stock -> copy(stock = cards)
        Pile.Waste -> copy(waste = cards)
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[p.i] = cards })
        is Pile.Tableau -> copy(tableau = tableau.toMutableList().also { it[p.i] = cards })
    }

    /** Computes a structural hash of the stock and waste card sequences. */
    fun stockWasteHash(): Long {
        var h = HASH_INIT
        for (c in stock) h = h * HASH_MULT + c.id + HASH_CARD_OFFSET
        h = h * HASH_MULT + HASH_SEPARATOR
        for (c in waste) h = h * HASH_MULT + c.id + HASH_CARD_OFFSET
        return h
    }

    /** Computes a structural board hash ignoring score and move counter. */
    fun boardHash(): Long {
        var h = HASH_INIT
        for (f in foundations) {
            val suitOrd = f.firstOrNull()?.suit?.ordinal?.toLong() ?: HASH_EMPTY_FOUNDATION_SUIT
            h = h * HASH_MULT + suitOrd * HASH_FOUNDATION_SUIT_MULT + f.size
        }
        for (col in tableau) {
            h = h * HASH_MULT + HASH_TABLEAU_SEPARATOR
            for (c in col) {
                val faceBit = if (c.faceUp) 1L else 0L
                h = h * HASH_MULT + (c.id * HASH_FACE_UP_MULT + faceBit)
            }
        }
        h = h * HASH_MULT + HASH_STOCK_SEPARATOR
        for (c in stock) h = h * HASH_MULT + c.id + HASH_CARD_OFFSET
        h = h * HASH_MULT + HASH_SEPARATOR
        for (c in waste) h = h * HASH_MULT + c.id + HASH_CARD_OFFSET
        return h
    }

    companion object {
        const val HASH_INIT = 1125899906842597L

        // Prime larger than the maximum encoded token value (<= 112) to avoid short-sequence collisions.
        const val HASH_MULT = 131L
        const val HASH_SEPARATOR = 99L
        const val HASH_TABLEAU_SEPARATOR = 109L
        const val HASH_STOCK_SEPARATOR = 110L
        const val HASH_CARD_OFFSET = 1L
        const val HASH_FACE_UP_MULT = 2L
        const val HASH_EMPTY_FOUNDATION_SUIT = -1L
        const val HASH_FOUNDATION_SUIT_MULT = 16L
    }
}
