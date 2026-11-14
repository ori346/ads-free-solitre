package com.adfree.solitaire.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolverTest {
    private fun replayMoves(initial: GameState, moves: List<SolverMove>): GameState {
        var curr = initial
        for ((stepIndex, step) in moves.withIndex()) {
            curr = when (step) {
                SolverMove.Draw -> {
                    val next = Engine.draw(curr)
                    assertTrue(
                        "Step #$stepIndex (Draw) returned null on state with stock=${curr.stock.size}, waste=${curr.waste.size}",
                        next != null,
                    )
                    next!!
                }
                is SolverMove.Move -> {
                    assertTrue(
                        "Step #$stepIndex (${step.from}[${step.index}] -> ${step.to}) was illegal",
                        Engine.canMove(curr, step.from, step.index, step.to),
                    )
                    val next = Engine.move(curr, step.from, step.index, step.to)
                    assertTrue(
                        "Step #$stepIndex (${step.from}[${step.index}] -> ${step.to}) returned null",
                        next != null,
                    )
                    next!!
                }
            }
        }
        return curr
    }

    private fun deadlockedTableausWithNonEmptyColumns(): List<List<Card>> = listOf(
        listOf(Card(Suit.SPADES, 2, true)),
        listOf(Card(Suit.SPADES, 4, true)),
        listOf(Card(Suit.SPADES, 6, true)),
        listOf(Card(Suit.SPADES, 8, true)),
        listOf(Card(Suit.SPADES, 10, true)),
        listOf(Card(Suit.SPADES, 12, true)),
        listOf(Card(Suit.CLUBS, 13, true)),
    )

    @Test fun omniscientSolvableDealsReplayToWinDraw1() {
        var solvableCount = 0
        for (seed in 1L..15L) {
            val deal = Engine.dealRawGame(drawCount = 1, random = Random(seed))
            val result = SolitaireSolver.solveOmniscient(deal)
            if (result.isSolvable) {
                solvableCount++
                assertTrue("Winning move list should not be empty for seed=$seed", result.winningMoves.isNotEmpty())
                val finalState = replayMoves(deal, result.winningMoves)
                assertTrue("Replayed winningMoves did not win on Draw-1 seed=$seed", finalState.isWon)
            }
        }
        assertTrue("Expected multiple solvable Draw-1 deals across seeds 1..15, got $solvableCount", solvableCount >= 3)
    }

    @Test fun omniscientSolvableDealsReplayToWinDraw3() {
        var solvableCount = 0
        var unsolvableCount = 0
        for (seed in 1L..25L) {
            val deal = Engine.dealRawGame(drawCount = 3, random = Random(seed))
            val result = SolitaireSolver.solveOmniscient(deal)
            if (result.isSolvable) {
                solvableCount++
                assertTrue("Winning move list should not be empty for Draw-3 seed=$seed", result.winningMoves.isNotEmpty())
                val finalState = replayMoves(deal, result.winningMoves)
                assertTrue("Replayed winningMoves did not win on Draw-3 seed=$seed", finalState.isWon)
            } else {
                unsolvableCount++
            }
        }
        assertTrue("Expected solvable Draw-3 deals in seeds 1..25, got $solvableCount", solvableCount >= 2)
        assertTrue("Expected unsolvable Draw-3 deals in seeds 1..25, got $unsolvableCount", unsolvableCount >= 1)
    }

    @Test fun rejectsDeadlockedHiddenAceUnderImmovableCards() {
        val state1 = GameState(
            stock = listOf(Card(Suit.HEARTS, 10, false), Card(Suit.DIAMONDS, 10, false)),
            waste = listOf(Card(Suit.CLUBS, 10, true)),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.DIAMONDS, 1, false), Card(Suit.SPADES, 2, true)),
                listOf(Card(Suit.SPADES, 4, true)),
                listOf(Card(Suit.SPADES, 6, true)),
                listOf(Card(Suit.SPADES, 8, true)),
                listOf(Card(Suit.SPADES, 10, true)),
                listOf(Card(Suit.SPADES, 12, true)),
                listOf(Card(Suit.CLUBS, 13, true)),
            ),
            drawCount = 1,
        )
        assertTrue(SolitaireSolver.hasStructuralDeadlock(state1))
        assertFalse(SolitaireSolver.isSolvableOmniscient(state1))

        // Variant with Ace of Hearts buried under 3♣ while 4♥/4♦ are also locked away.
        val state2 = GameState(
            stock = listOf(Card(Suit.HEARTS, 6, false), Card(Suit.DIAMONDS, 6, false)),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.HEARTS, 1, false), Card(Suit.CLUBS, 3, true)),
                listOf(Card(Suit.SPADES, 5, true)),
                listOf(Card(Suit.CLUBS, 7, true)),
                listOf(Card(Suit.SPADES, 9, true)),
                listOf(Card(Suit.CLUBS, 11, true)),
                listOf(Card(Suit.SPADES, 13, true)),
                listOf(Card(Suit.CLUBS, 13, true)),
            ),
            drawCount = 3,
        )
        assertTrue(SolitaireSolver.hasStructuralDeadlock(state2))
        assertFalse(SolitaireSolver.isSolvableOmniscient(state2))
    }

    @Test fun rejectsMutualCyclicBlockageBetweenTableauColumns() {
        // Col 0 hides 4♥/4♦/A♠/2♠/3♠ beneath 4♠ (needs 5♥/5♦ or A..3♠).
        // Col 1 hides 5♥/5♦/A♣/2♣ beneath 3♣ (needs 4♥/4♦ or A..2♣).
        // Remaining 41 cards cannot provide any red 4, red 5, or low ♠/♣ cards.
        val col0 = listOf(
            Card(Suit.SPADES, 1, false),
            Card(Suit.SPADES, 2, false),
            Card(Suit.SPADES, 3, false),
            Card(Suit.HEARTS, 4, false),
            Card(Suit.DIAMONDS, 4, false),
            Card(Suit.SPADES, 4, true),
        )
        val col1 = listOf(
            Card(Suit.CLUBS, 1, false),
            Card(Suit.CLUBS, 2, false),
            Card(Suit.HEARTS, 5, false),
            Card(Suit.DIAMONDS, 5, false),
            Card(Suit.CLUBS, 3, true),
        )
        val lockedIds = (col0 + col1).map { it.id }.toSet()
        val remaining = Suit.entries
            .flatMap { suit -> (1..13).map { rank -> Card(suit, rank, false) } }
            .filter { it.id !in lockedIds }
        val state = GameState(
            stock = remaining.drop(5),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                col0,
                col1,
                listOf(remaining[0].copy(faceUp = true)),
                listOf(remaining[1].copy(faceUp = true)),
                listOf(remaining[2].copy(faceUp = true)),
                listOf(remaining[3].copy(faceUp = true)),
                listOf(remaining[4].copy(faceUp = true)),
            ),
            drawCount = 1,
        )

        val allIds = (state.stock + state.waste + state.foundations.flatten() + state.tableau.flatten()).map { it.id }
        assertEquals(52, allIds.toSet().size)
        assertTrue(SolitaireSolver.hasStructuralDeadlock(state))
        assertFalse(SolitaireSolver.isSolvableOmniscient(state))
    }

    @Test fun rejectsDraw3ModuloLockWhenPlayableCardsAtUnreachableIndices() {
        // In a 6-card Draw-3 stock with no moves on the board, only stock[3] and stock[0] appear on waste top.
        // Indices 1, 2, 4, 5 hold all 4 Aces and are permanently unreachable in Draw-3.
        val stock = listOf(
            Card(Suit.HEARTS, 10, false), // idx 0 (reachable)
            Card(Suit.SPADES, 1, false), // idx 1 (unreachable)
            Card(Suit.HEARTS, 1, false), // idx 2 (unreachable)
            Card(Suit.DIAMONDS, 10, false), // idx 3 (reachable)
            Card(Suit.DIAMONDS, 1, false), // idx 4 (unreachable)
            Card(Suit.CLUBS, 1, false), // idx 5 (unreachable)
        )
        val draw3Locked = GameState(
            stock = stock,
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = deadlockedTableausWithNonEmptyColumns(),
            drawCount = 3,
        )
        assertFalse(SolitaireSolver.isSolvableOmniscient(draw3Locked))

        // Full 52-card Draw-3 modulo lock: foundations at 1..8, 7 non-empty deadlocked columns,
        // and only unplayable K♥ (idx 0) and K♦ (idx 3) reachable in a 6-card stock.
        val foundations = Suit.entries.map { s -> (1..8).map { r -> Card(s, r, true) } }
        val tableaus = listOf(
            listOf(Card(Suit.SPADES, 9, true), Card(Suit.SPADES, 12, true)),
            listOf(Card(Suit.CLUBS, 9, true), Card(Suit.CLUBS, 12, true)),
            listOf(Card(Suit.HEARTS, 10, true), Card(Suit.SPADES, 11, true)),
            listOf(Card(Suit.DIAMONDS, 10, true), Card(Suit.CLUBS, 11, true)),
            listOf(Card(Suit.DIAMONDS, 9, true), Card(Suit.SPADES, 13, true)),
            listOf(Card(Suit.HEARTS, 9, true), Card(Suit.CLUBS, 13, true)),
            listOf(Card(Suit.SPADES, 10, true), Card(Suit.CLUBS, 10, true)),
        )
        val fullStock = listOf(
            Card(Suit.HEARTS, 13, false), // idx 0 (reachable, blocked because all 7 columns non-empty)
            Card(Suit.HEARTS, 11, false), // idx 1 (unreachable)
            Card(Suit.DIAMONDS, 11, false), // idx 2 (unreachable)
            Card(Suit.DIAMONDS, 13, false), // idx 3 (reachable, blocked because all 7 columns non-empty)
            Card(Suit.HEARTS, 12, false), // idx 4 (unreachable)
            Card(Suit.DIAMONDS, 12, false), // idx 5 (unreachable)
        )
        val fullDraw3Locked = GameState(fullStock, emptyList(), foundations, tableaus, drawCount = 3)
        val allIds = (fullDraw3Locked.stock + fullDraw3Locked.foundations.flatten() + fullDraw3Locked.tableau.flatten()).map { it.id }
        assertEquals(52, allIds.toSet().size)
        assertFalse(SolitaireSolver.isSolvableOmniscient(fullDraw3Locked))
    }

    @Test fun rejectsNonDescendingFaceUpTableauDeadlockForAutoCompleteAndSolver() {
        val state = GameState(
            stock = emptyList(),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.DIAMONDS, 1, true), Card(Suit.SPADES, 2, true)),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            ),
            drawCount = 1,
        )
        assertFalse(Engine.canAutoComplete(state))
        assertFalse(SolitaireSolver.isSolvableOmniscient(state))
    }

    @Test fun rejectsRandomizedImpossibleDealsWithBuriedAcesAndDeadlockedTops() {
        val deadlockedTopCards = listOf(
            Card(Suit.SPADES, 2, true),
            Card(Suit.SPADES, 4, true),
            Card(Suit.SPADES, 6, true),
            Card(Suit.SPADES, 8, true),
            Card(Suit.SPADES, 10, true),
            Card(Suit.SPADES, 12, true),
            Card(Suit.CLUBS, 13, true),
        )
        val usedIds = deadlockedTopCards.map { it.id }.toSet()
        val buriedPool = Suit.entries
            .flatMap { s -> (1..13).map { r -> Card(s, r, false) } }
            .filter { it.id !in usedIds }

        for (seed in 100L..107L) {
            val shuffled = buriedPool.shuffled(Random(seed))
            // Keep all 4 Aces and non-playing cards buried face-down under deadlocked tops,
            // with only unplayable high red cards (no Jacks/Kings) in stock.
            val aces = shuffled.filter { it.isAce }
            val nonAces = shuffled.filter { !it.isAce && it.rank != 13 && it.suit.isRed && it.rank % 2 == 0 }
            val cols = deadlockedTopCards.mapIndexed { idx, top ->
                val hidden = if (idx < aces.size) listOf(aces[idx]) else emptyList()
                hidden + top
            }
            val stock = nonAces.take(4)
            val impossible = GameState(stock, emptyList(), List(4) { emptyList() }, cols, drawCount = (seed % 2).toInt() * 2 + 1)
            assertTrue("Expected structural deadlock on seed=$seed", SolitaireSolver.hasStructuralDeadlock(impossible))
            assertFalse("Expected unsolvable deal on seed=$seed", SolitaireSolver.isSolvableOmniscient(impossible))
        }
    }

    @Test fun solvesBoardRequiringEmptyingColumnForKingBuriedInStock() {
        // 52-card solvable state where K♠ is buried at stock[0] (NOT on waste top).
        // Col 0 hides 8♠, 8♣, 8♦, 10♥, J♥ beneath Q♥; the ONLY way to uncover Col 0 is
        // to move Q♣ (Col 1, index 0) onto K♥ (Col 2) so Col 1 becomes empty for K♠ from Stock!
        val foundations = listOf(
            (1..7).map { Card(Suit.SPADES, it, true) },
            (1..9).map { Card(Suit.HEARTS, it, true) },
            (1..7).map { Card(Suit.DIAMONDS, it, true) },
            (1..7).map { Card(Suit.CLUBS, it, true) },
        )
        val tableaus = listOf(
            listOf(
                Card(Suit.SPADES, 8, false),
                Card(Suit.CLUBS, 8, false),
                Card(Suit.DIAMONDS, 8, false),
                Card(Suit.HEARTS, 10, false),
                Card(Suit.HEARTS, 11, false),
                Card(Suit.HEARTS, 12, true),
            ),
            listOf(Card(Suit.CLUBS, 12, true)),
            listOf(Card(Suit.HEARTS, 13, true)),
            listOf(Card(Suit.DIAMONDS, 13, true), Card(Suit.SPADES, 12, true)),
            listOf(Card(Suit.CLUBS, 13, true), Card(Suit.DIAMONDS, 12, true)),
            listOf(Card(Suit.SPADES, 9, false), Card(Suit.SPADES, 10, false), Card(Suit.DIAMONDS, 11, true)),
            listOf(
                Card(Suit.CLUBS, 9, false),
                Card(Suit.DIAMONDS, 9, false),
                Card(Suit.CLUBS, 10, false),
                Card(Suit.DIAMONDS, 10, true),
            ),
        )
        val stock = listOf(
            Card(Suit.SPADES, 13, false),
            Card(Suit.SPADES, 11, false),
            Card(Suit.CLUBS, 11, false),
        )
        val state = GameState(stock, emptyList(), foundations, tableaus, drawCount = 1)
        val allIds = (state.stock + state.foundations.flatten() + state.tableau.flatten()).map { it.id }
        assertEquals(52, allIds.toSet().size)

        val result = SolitaireSolver.solveOmniscient(state)
        assertTrue("Solver must detect K♠ buried in stock and empty Col 1 to win", result.isSolvable)
        val finalState = replayMoves(state, result.winningMoves)
        assertTrue("Replaying King-in-stock solution must win", finalState.isWon)
    }

    @Test fun gameDealerEnsureSolvableGuaranteesReplayableWinAcrossModes() {
        for (seed in 1L..8L) {
            val draw1 = GameDealer.newGame(drawCount = 1, seed = seed, ensureSolvable = true)
            val res1 = SolitaireSolver.solveOmniscient(draw1)
            assertTrue("Draw-1 deal for seed=$seed must be solvable", res1.isSolvable)
            assertTrue("Draw-1 deal for seed=$seed must replay to win", replayMoves(draw1, res1.winningMoves).isWon)
        }
        for (seed in 1L..8L) {
            val draw3 = GameDealer.newGame(drawCount = 3, seed = seed, ensureSolvable = true)
            val res3 = SolitaireSolver.solveOmniscient(draw3)
            assertTrue("Draw-3 deal for seed=$seed must be solvable", res3.isSolvable)
            assertTrue("Draw-3 deal for seed=$seed must replay to win", replayMoves(draw3, res3.winningMoves).isWon)
        }
    }

    @Test fun solveOmniscientRespectsMaxDurationAndInteractiveBudget() {
        val deal = GameDealer.newGame(drawCount = 1, seed = 2L, ensureSolvable = true)
        val solved = Engine.solveOmniscient(
            initial = deal,
            maxStates = 250_000,
            maxDurationMs = 5_500L,
        )
        assertTrue(solved.isSolvable)
        assertTrue(replayMoves(deal, solved.winningMoves).isWon)

        // A zero-millisecond budget stops at the first deadline check without crashing.
        val zeroBudget = Engine.solveOmniscient(
            initial = deal,
            maxStates = 250_000,
            maxDurationMs = 0L,
        )
        assertFalse(zeroBudget.isSolvable)
        assertEquals(0, zeroBudget.exploredStates)
    }

    @Test fun solverNeverDrawsOverExposedAceOnWasteAndProducesCompactSolutions() {
        for (seed in 1L..15L) {
            val deal = Engine.dealRawGame(drawCount = 1, random = Random(seed))
            val result = SolitaireSolver.solveOmniscient(deal)
            if (!result.isSolvable) continue

            assertTrue(
                "Expected compact winning move sequence (<= 150 moves) for seed=$seed, got ${result.winningMoves.size}",
                result.winningMoves.size <= 150,
            )

            var curr = deal
            for ((stepIndex, step) in result.winningMoves.withIndex()) {
                if (step == SolverMove.Draw && curr.waste.isNotEmpty()) {
                    assertFalse(
                        "Solver drew over an exposed Ace (${curr.waste.last()}) on waste at step #$stepIndex on seed=$seed",
                        curr.waste.last().isAce,
                    )
                }
                curr = when (step) {
                    SolverMove.Draw -> Engine.draw(curr)!!
                    is SolverMove.Move -> Engine.move(curr, step.from, step.index, step.to)!!
                }
            }
            assertTrue(curr.isWon)
        }
    }
}
