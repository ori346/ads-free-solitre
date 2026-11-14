package com.adfree.solitaire.config

/**
 * Core Klondike Solitaire rule, limit, and timing parameters.
 *
 * @property suitsCount Number of suits in the deck.
 * @property ranksPerSuit Number of ranks per suit.
 * @property tableauColumnCount Number of tableau columns on the board.
 * @property defaultDrawCount Default stock draw count for new installations.
 * @property supportedDrawCounts Supported draw count options exposed in settings.
 * @property maxUndoHistory Maximum number of in-memory undo states retained.
 * @property persistedHistoryLimit Maximum number of undo states persisted to storage.
 * @property leaderboardSize Maximum number of top score entries retained.
 * @property maxSolvableDealAttempts Maximum deal retries when searching for a solvable deal.
 * @property timerTickMs Interval between game clock increments in milliseconds.
 * @property aceRank Numeric rank of an Ace.
 * @property jackRank Numeric rank of a Jack.
 * @property queenRank Numeric rank of a Queen.
 * @property kingRank Numeric rank of a King.
 */
data class GameRulesConfig(
    val suitsCount: Int = 4,
    val ranksPerSuit: Int = 13,
    val tableauColumnCount: Int = 7,
    val defaultDrawCount: Int = 1,
    val supportedDrawCounts: List<Int> = listOf(1, 3),
    val maxUndoHistory: Int = 500,
    val persistedHistoryLimit: Int = 100,
    val leaderboardSize: Int = 10,
    val maxSolvableDealAttempts: Int = 200,
    val timerTickMs: Long = 1_000L,
    val aceRank: Int = 1,
    val jackRank: Int = 11,
    val queenRank: Int = 12,
    val kingRank: Int = 13,
) {
    /** Total number of cards in a standard deck. */
    val deckSize: Int get() = suitsCount * ranksPerSuit
}

/**
 * Scoring rewards and penalties for Klondike Solitaire moves.
 *
 * @property toFoundationPoints Points awarded for moving a card to a foundation pile.
 * @property wasteToTableauPoints Points awarded for moving a card from waste to tableau.
 * @property fromFoundationPenalty Score delta applied when moving a card back from foundation.
 * @property flipTableauCardPoints Points awarded when flipping a face-down tableau card.
 * @property recyclePenaltyDraw1 Points deducted when recycling waste in Draw-1 mode.
 * @property recyclePenaltyDraw3 Points deducted when recycling waste in Draw-3 mode.
 * @property minScore Minimum floor for the game score.
 */
data class ScoringConfig(
    val toFoundationPoints: Int = 10,
    val wasteToTableauPoints: Int = 5,
    val fromFoundationPenalty: Int = -15,
    val flipTableauCardPoints: Int = 5,
    val recyclePenaltyDraw1: Int = 100,
    val recyclePenaltyDraw3: Int = 20,
    val minScore: Int = 0,
)

/**
 * Weights used by tap-to-move heuristics when ranking legal destination piles.
 *
 * @property baseTableauScore Baseline score for moving a stack onto a non-empty tableau column.
 * @property lateralParentSwapPenalty Penalty for swapping between equivalent alternating parents.
 * @property buryImmediateFoundationPenalty Penalty for covering a card immediately playable to foundation.
 * @property buryNextFoundationExposedPenalty Penalty when covering a card 2 ranks above foundation with the intervening card exposed.
 * @property buryNextFoundationUnexposedPenalty Penalty when covering a card 2 ranks above foundation with the intervening card unexposed.
 * @property buryBlockingFoundationCardPenalty Penalty when burying a deeper face-up card already playable to foundation.
 * @property hiddenCardPenalty Per-card penalty for face-down cards locked beneath the destination column.
 * @property unanchoredSingleCardPenalty Penalty for burying a lone non-King card that directly blocks a hidden card or empty column.
 * @property establishedRunBonus Bonus for extending an already-anchored face-up run or King column.
 * @property faceUpRunCardPenalty Optional per-card penalty for the destination column's existing face-up run length.
 * @property columnTotalCardPenalty Optional per-card penalty for the destination column's total card count.
 * @property foundationGapBonusMultiplier Bonus multiplier per rank gap above the foundation top.
 * @property maxFoundationGapBonus Maximum rank gap considered for the foundation gap bonus.
 * @property emptyColumnKingScore Score assigned to moving a King onto an empty tableau column.
 */
data class TapHeuristicConfig(
    val baseTableauScore: Int = 100,
    val lateralParentSwapPenalty: Int = 40,
    val buryImmediateFoundationPenalty: Int = 80,
    val buryNextFoundationExposedPenalty: Int = 45,
    val buryNextFoundationUnexposedPenalty: Int = 20,
    val buryBlockingFoundationCardPenalty: Int = 35,
    val hiddenCardPenalty: Int = 8,
    val unanchoredSingleCardPenalty: Int = 10,
    val establishedRunBonus: Int = 6,
    val faceUpRunCardPenalty: Int = 0,
    val columnTotalCardPenalty: Int = 0,
    val foundationGapBonusMultiplier: Int = 2,
    val maxFoundationGapBonus: Int = 4,
    val emptyColumnKingScore: Int = 0,
)

/**
 * Search budget, heuristic weights, and auto-complete bounds for the solver.
 *
 * @property defaultMaxStates Maximum states explored during omniscient solvability checks.
 * @property interactiveMaxStates Maximum states explored when the user interactively runs the board solver.
 * @property interactiveMaxDurationMs Maximum wall-clock time in milliseconds for an interactive solver run.
 * @property interactiveMinDisplayMs Minimum spinner display time in milliseconds for an interactive solver check.
 * @property foundationCardReward Heuristic reward per card already placed on foundations.
 * @property hiddenCardBasePenalty Base heuristic penalty per face-down tableau card.
 * @property hiddenNextFoundationUrgency Extra penalty when a face-down card is immediately needed on foundation.
 * @property hiddenLowRankWeight Weight favoring uncovering lower-rank hidden cards sooner.
 * @property emptyColumnReward Heuristic reward per empty tableau column.
 * @property stockWasteCardPenalty Heuristic penalty per remaining card in stock or waste.
 * @property autoCompleteStallMultiplier Multiplier applied to remaining board cards for auto-complete cycle limit.
 * @property autoCompleteStallBaseMargin Base margin added to the auto-complete stall step limit.
 * @property safeFoundationRankThreshold Maximum rank difference across opposite-color foundations considered safe to auto-promote.
 */
data class SolverConfig(
    val defaultMaxStates: Int = 6_000,
    val interactiveMaxStates: Int = 250_000,
    val interactiveMaxDurationMs: Long = 5_500L,
    val interactiveMinDisplayMs: Long = 350L,
    val foundationCardReward: Int = 120,
    val hiddenCardBasePenalty: Int = 35,
    val hiddenNextFoundationUrgency: Int = 45,
    val hiddenLowRankWeight: Int = 2,
    val emptyColumnReward: Int = 15,
    val stockWasteCardPenalty: Int = 4,
    val autoCompleteStallMultiplier: Int = 3,
    val autoCompleteStallBaseMargin: Int = 30,
    val safeFoundationRankThreshold: Int = 2,
)

/**
 * Aggregate gameplay, scoring, tap heuristic, and solver configuration.
 *
 * @property rules Core Klondike rules and limits.
 * @property scoring Move scoring rules.
 * @property tapHeuristics Tap-to-move destination ranking weights.
 * @property solver Omniscient solver and auto-complete configuration.
 */
data class GameConfig(
    val rules: GameRulesConfig = GameRulesConfig(),
    val scoring: ScoringConfig = ScoringConfig(),
    val tapHeuristics: TapHeuristicConfig = TapHeuristicConfig(),
    val solver: SolverConfig = SolverConfig(),
)

/** Default configuration used across the game engine, solver, and storage. */
val DefaultGameConfig = GameConfig()
