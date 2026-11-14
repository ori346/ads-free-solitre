package com.adfree.solitaire.game

/** Playing card suits in standard Klondike order. */
enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠\uFE0E", false),
    HEARTS("♥\uFE0E", true),
    DIAMONDS("♦\uFE0E", true),
    CLUBS("♣\uFE0E", false),
}

/**
 * Immutable playing card model.
 *
 * @property suit Card suit.
 * @property rank Numeric rank from 1 (Ace) to 13 (King).
 * @property faceUp Whether the card face is currently visible.
 */
data class Card(val suit: Suit, val rank: Int, val faceUp: Boolean = false) {
    /** Unique compact identifier in `0..51`. */
    val id: Int get() = suit.ordinal * RANKS_PER_SUIT + rank - 1

    /** Display rank label (`A`, `2`..`10`, `J`, `Q`, `K`). */
    val rankLabel: String
        get() = when (rank) {
            RANK_ACE -> "A"
            RANK_JACK -> "J"
            RANK_QUEEN -> "Q"
            RANK_KING -> "K"
            else -> rank.toString()
        }

    /** True if this card is an Ace. */
    val isAce: Boolean get() = rank == RANK_ACE

    /** True if this card is a King. */
    val isKing: Boolean get() = rank == RANK_KING

    /** True if this card is a court card (Jack, Queen, or King). */
    val isRoyalty: Boolean get() = rank in RANK_JACK..RANK_KING

    /** True when [other] has the opposite suit color (red vs black). */
    fun hasOppositeColor(other: Card): Boolean = suit.isRed != other.suit.isRed

    companion object {
        /** Total number of cards in a standard 52-card deck (`4 * 13`). */
        const val DECK_SIZE = 52
        const val RANKS_PER_SUIT = 13
        const val RANK_ACE = 1
        const val RANK_TWO = 2
        const val RANK_THREE = 3
        const val RANK_TEN = 10
        const val RANK_JACK = 11
        const val RANK_QUEEN = 12
        const val RANK_KING = 13

        /** Reconstructs a [Card] from its compact [id] and visibility flag [faceUp]. */
        fun fromId(id: Int, faceUp: Boolean): Card =
            Card(Suit.entries[id / RANKS_PER_SUIT], id % RANKS_PER_SUIT + 1, faceUp)

        /** Creates a full unshuffled 52-card deck with each card's visibility set to [faceUp]. */
        fun standardDeck(
            ranksPerSuit: Int = RANKS_PER_SUIT,
            faceUp: Boolean = false,
        ): List<Card> = Suit.entries.flatMap { suit ->
            (RANK_ACE..ranksPerSuit).map { rank -> Card(suit, rank, faceUp) }
        }
    }
}

/** Identifies a pile on the Klondike board. */
sealed interface Pile {
    data object Stock : Pile
    data object Waste : Pile
    data class Foundation(val i: Int) : Pile
    data class Tableau(val i: Int) : Pile
}

/** Suggested move returned by the hint engine. */
data class Hint(val from: Pile, val index: Int, val to: Pile)
