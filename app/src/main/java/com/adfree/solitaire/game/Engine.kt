package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Public facade delegating Klondike dealing, rules, tap heuristics, hints, auto-completion, and solving
 * to the focused game-layer modules.
 */
object Engine {
    private val secureRandom: Random = SecureRandom().asKotlinRandom()

    val allFoundations: List<Pile.Foundation> get() = GameRules.allFoundations
    val allTableaus: List<Pile.Tableau> get() = GameRules.allTableaus

    /** When true, prints a message whenever an unsolvable deal is rejected and redrawn. */
    var debugMode: Boolean = false

    fun dealRawGame(drawCount: Int, random: Random = secureRandom): GameState =
        GameDealer.dealRawGame(drawCount, random)

    fun newGame(
        drawCount: Int,
        random: Random = secureRandom,
        ensureSolvable: Boolean = true,
        debug: Boolean = debugMode,
        onUnsolvableDeal: ((attempt: Int, state: GameState) -> Unit)? = null,
    ): GameState = GameDealer.newGame(
        drawCount = drawCount,
        random = random,
        ensureSolvable = ensureSolvable,
        debug = debug,
        onUnsolvableDeal = onUnsolvableDeal,
    )

    fun newGame(
        drawCount: Int,
        seed: Long,
        ensureSolvable: Boolean = true,
        debug: Boolean = debugMode,
    ): GameState = GameDealer.newGame(
        drawCount = drawCount,
        seed = seed,
        ensureSolvable = ensureSolvable,
        debug = debug,
    )

    /** Draw from stock, or recycle the waste when the stock is empty. */
    fun draw(s: GameState, applyPenalty: Boolean = true): GameState? =
        GameRules.draw(s, applyPenalty)

    fun canPlaceOnFoundation(f: List<Card>, c: Card): Boolean =
        GameRules.canPlaceOnFoundation(f, c)

    fun canPlaceOnFoundation(s: GameState, f: Pile.Foundation, c: Card): Boolean =
        GameRules.canPlaceOnFoundation(s, f, c)

    fun canPlaceOnTableau(t: List<Card>, c: Card): Boolean =
        GameRules.canPlaceOnTableau(t, c)

    fun isValidRun(cards: List<Card>): Boolean =
        GameRules.isValidRun(cards)

    fun canMove(s: GameState, from: Pile, index: Int, to: Pile): Boolean =
        GameRules.canMove(s, from, index, to)

    fun move(s: GameState, from: Pile, index: Int, to: Pile): GameState? =
        GameRules.move(s, from, index, to)

    fun canMoveToAnyTarget(s: GameState, from: Pile, index: Int): Boolean =
        GameRules.canMoveToAnyTarget(s, from, index)

    /** Scores a legal tableau destination [to] for moving [from] starting at [index]. */
    fun scoreTableauTarget(s: GameState, from: Pile, index: Int, to: Pile.Tableau): Int =
        TapTargetScorer.scoreTableauTarget(s, from, index, to)

    /** All legal destinations for a card/stack at [from]/[index], ordered from smartest to worst. */
    fun rankedTargets(s: GameState, from: Pile, index: Int): List<Pile> =
        TapTargetScorer.rankedTargets(s, from, index)

    /** Best destination for a tapped card (foundation first, then highest-scored tableau). */
    fun bestTarget(s: GameState, from: Pile, index: Int, avoidTargets: Set<Pile> = emptySet()): Pile? =
        TapTargetScorer.bestTarget(s, from, index, avoidTargets)

    /** Resolves a tap on [from] at [index], cycling away from [avoidTargets]. */
    fun bestTapMove(
        s: GameState,
        from: Pile,
        index: Int,
        avoidTargets: Set<Pile> = emptySet(),
    ): Pair<Int, Pile>? = TapTargetScorer.bestTapMove(s, from, index, avoidTargets)

    /** Resolves the highest valid draggable stack start index containing [index] in [pile]. */
    fun resolveDraggableStackStartIndex(s: GameState, pile: Pile, index: Int): Int =
        TapTargetScorer.resolveDraggableStackStartIndex(s, pile, index)

    fun findHints(s: GameState): List<Hint> = HintFinder.findHints(s)

    fun findHint(s: GameState): Hint? = HintFinder.findHint(s)

    fun canStockCycleProduceMove(s: GameState): Boolean = HintFinder.canStockCycleProduceMove(s)

    fun canAutoComplete(s: GameState): Boolean = AutoCompleter.canAutoComplete(s)

    fun autoCompleteStallLimit(s: GameState): Int = AutoCompleter.autoCompleteStallLimit(s)

    fun simulateAutoComplete(s: GameState): GameState? = AutoCompleter.simulateAutoComplete(s)

    fun autoCompleteStep(s: GameState): GameState? = AutoCompleter.autoCompleteStep(s)

    fun isSolvableOmniscient(
        initial: GameState,
        maxStates: Int = DefaultGameConfig.solver.defaultMaxStates,
    ): Boolean = SolitaireSolver.isSolvableOmniscient(initial, maxStates)

    fun solveOmniscient(
        initial: GameState,
        maxStates: Int = DefaultGameConfig.solver.defaultMaxStates,
        maxDurationMs: Long = Long.MAX_VALUE,
    ): SolverResult = SolitaireSolver.solveOmniscient(
        initial = initial,
        maxStates = maxStates,
        maxDurationMs = maxDurationMs,
    )
}
