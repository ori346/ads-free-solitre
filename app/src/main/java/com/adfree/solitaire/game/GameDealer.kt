package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.GameConfig
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Deals standard 52-card Klondike layouts and optionally filters deals for omniscient solvability.
 */
object GameDealer {
    private val secureRandom: Random = SecureRandom().asKotlinRandom()

    /** Deals a raw shuffled 52-card Klondike [GameState] using [random] and [config]. */
    fun dealRawGame(
        drawCount: Int,
        random: Random = secureRandom,
        config: GameConfig = DefaultGameConfig,
    ): GameState {
        val deck = Card.standardDeck(ranksPerSuit = config.rules.ranksPerSuit).shuffled(random)
        var idx = 0
        val tab = (0 until config.rules.tableauColumnCount).map { col ->
            (0..col).map { row -> deck[idx++].copy(faceUp = row == col) }
        }
        return GameState(
            stock = deck.drop(idx),
            waste = emptyList(),
            foundations = List(config.rules.suitsCount) { emptyList() },
            tableau = tab,
            drawCount = drawCount,
        )
    }

    /**
     * Deals a new game, redrawing up to `config.rules.maxSolvableDealAttempts` times until
     * [SolitaireSolver.isSolvableOmniscient] succeeds when [ensureSolvable] is enabled.
     */
    fun newGame(
        drawCount: Int,
        random: Random = secureRandom,
        ensureSolvable: Boolean = true,
        debug: Boolean = false,
        config: GameConfig = DefaultGameConfig,
        onUnsolvableDeal: ((attempt: Int, state: GameState) -> Unit)? = null,
    ): GameState {
        var attempts = 0
        while (true) {
            attempts++
            val candidate = dealRawGame(drawCount, random, config)
            if (!ensureSolvable || SolitaireSolver.isSolvableOmniscient(candidate, config.solver.defaultMaxStates)) {
                if (debug && attempts > 1) {
                    println("[Solitaire Debug] Selected solvable deal on attempt #$attempts (drawCount=$drawCount)")
                }
                return candidate
            }
            if (debug) {
                val topTableau = candidate.tableau.mapIndexed { i, col ->
                    val c = col.last()
                    "Col${i + 1}:${c.rankLabel}${c.suit.name.first()}"
                }.joinToString(" ")
                println("[Solitaire Debug] Unsolvable deal drawn on attempt #$attempts (drawCount=$drawCount, $topTableau) - redrawing...")
            }
            onUnsolvableDeal?.invoke(attempts, candidate)
            if (attempts >= config.rules.maxSolvableDealAttempts) return candidate
        }
    }

    /** Deterministic seeded overload for dealing a new game. */
    fun newGame(
        drawCount: Int,
        seed: Long,
        ensureSolvable: Boolean = true,
        debug: Boolean = false,
        config: GameConfig = DefaultGameConfig,
    ): GameState = newGame(
        drawCount = drawCount,
        random = Random(seed),
        ensureSolvable = ensureSolvable,
        debug = debug,
        config = config,
    )
}
