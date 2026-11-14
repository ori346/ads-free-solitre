package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.GameConfig
import com.adfree.solitaire.config.ScoringConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesAndInvariantsTest {
    private val fullDeckIds = (0 until DefaultGameConfig.rules.deckSize).toSet()

    private fun assertBoardInvariants(state: GameState, minScore: Int = DefaultGameConfig.scoring.minScore) {
        val allCards = state.stock + state.waste + state.foundations.flatten() + state.tableau.flatten()
        assertEquals("Total card count must always equal 52", 52, allCards.size)
        assertEquals("Card IDs must conserve exact set 0..51", fullDeckIds, allCards.map { it.id }.toSet())

        assertTrue("Score ${state.score} fell below minScore $minScore", state.score >= minScore)
        assertTrue("Stock cards must all remain face-down", state.stock.all { !it.faceUp })
        assertTrue("Waste cards must all be face-up", state.waste.all { it.faceUp })
        assertTrue("Foundation cards must all be face-up", state.foundations.flatten().all { it.faceUp })

        for ((colIdx, col) in state.tableau.withIndex()) {
            if (col.isEmpty()) continue
            assertTrue("Non-empty tableau column $colIdx top card must be face-up", col.last().faceUp)
            val faceUpTail = col.dropWhile { !it.faceUp }
            assertTrue(
                "No face-down card may appear below a face-up card in column $colIdx",
                faceUpTail.all { it.faceUp },
            )
            assertTrue(
                "Face-up tail in column $colIdx must form a valid alternating-color descending run",
                GameRules.isValidRun(faceUpTail),
            )
        }

        for ((fIdx, fPile) in state.foundations.withIndex()) {
            if (fPile.isEmpty()) continue
            val suit = fPile.first().suit
            assertTrue("Foundation $fIdx contains mixed suits", fPile.all { it.suit == suit })
            assertEquals(
                "Foundation $fIdx ranks must be strictly 1..${fPile.size}",
                (1..fPile.size).toList(),
                fPile.map { it.rank },
            )
        }
    }

    private fun collectAllLegalMoves(s: GameState): List<Triple<Pile, Int, Pile>> = buildList {
        if (s.waste.isNotEmpty()) {
            val wIdx = s.waste.lastIndex
            for (f in GameRules.allFoundations) {
                if (GameRules.canMove(s, Pile.Waste, wIdx, f)) add(Triple(Pile.Waste, wIdx, f))
            }
            for (t in GameRules.allTableaus) {
                if (GameRules.canMove(s, Pile.Waste, wIdx, t)) add(Triple(Pile.Waste, wIdx, t))
            }
        }
        for (t in GameRules.allTableaus) {
            val col = s.pile(t)
            for (idx in col.indices) {
                if (!col[idx].faceUp) continue
                if (idx == col.lastIndex) {
                    for (f in GameRules.allFoundations) {
                        if (GameRules.canMove(s, t, idx, f)) add(Triple(t, idx, f))
                    }
                }
                for (dest in GameRules.allTableaus) {
                    if (dest != t && GameRules.canMove(s, t, idx, dest)) add(Triple(t, idx, dest))
                }
            }
        }
        for (f in GameRules.allFoundations) {
            val fPile = s.pile(f)
            if (fPile.isEmpty()) continue
            val fIdx = fPile.lastIndex
            for (t in GameRules.allTableaus) {
                if (GameRules.canMove(s, f, fIdx, t)) add(Triple(f, fIdx, t))
            }
        }
    }

    @Test fun propertyBasedRandomizedSimulationsPreserveAllInvariants() {
        var sawDrawOrRecycle = false
        var sawFoundationMove = false
        var sawTableauFlip = false

        for (drawCount in listOf(1, 3)) {
            for (seed in 1L..8L) {
                val rng = Random(seed * 100L + drawCount)
                var curr = GameDealer.dealRawGame(drawCount, rng)
                assertBoardInvariants(curr)

                for (step in 0 until 200) {
                    if (curr.isWon) break
                    val legalMoves = collectAllLegalMoves(curr)
                    val canDraw = curr.stock.isNotEmpty() || curr.waste.isNotEmpty()
                    if (legalMoves.isEmpty() && !canDraw) break

                    val shouldDraw = canDraw && (legalMoves.isEmpty() || rng.nextInt(4) == 0)
                    curr = if (shouldDraw) {
                        sawDrawOrRecycle = true
                        GameRules.draw(curr)!!
                    } else {
                        val (from, index, to) = legalMoves[rng.nextInt(legalMoves.size)]
                        if (to is Pile.Foundation) sawFoundationMove = true
                        if (from is Pile.Tableau && index > 0 && !curr.pile(from)[index - 1].faceUp) {
                            sawTableauFlip = true
                        }
                        GameRules.move(curr, from, index, to)!!
                    }
                    assertBoardInvariants(curr)
                }
            }
        }

        assertTrue("Simulation should exercise stock draw/recycle", sawDrawOrRecycle)
        assertTrue("Simulation should exercise foundation promotions", sawFoundationMove)
        assertTrue("Simulation should exercise tableau flips", sawTableauFlip)
    }

    @Test fun customScoringConfigAppliesAllMoveAndRecycleRulesAccurately() {
        val customScoring = ScoringConfig(
            toFoundationPoints = 25,
            wasteToTableauPoints = 12,
            fromFoundationPenalty = -30,
            flipTableauCardPoints = 17,
            recyclePenaltyDraw1 = 150,
            recyclePenaltyDraw3 = 45,
            minScore = 10,
        )
        val config = GameConfig(scoring = customScoring)

        val baseState = GameState(
            stock = listOf(Card(Suit.HEARTS, 9, false)),
            waste = listOf(Card(Suit.SPADES, 1, true), Card(Suit.HEARTS, 6, true)),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.CLUBS, 10, false), Card(Suit.DIAMONDS, 1, true)),
                listOf(Card(Suit.SPADES, 7, true)),
                listOf(Card(Suit.DIAMONDS, 2, false), Card(Suit.CLUBS, 5, true)),
                listOf(Card(Suit.CLUBS, 8, true), Card(Suit.HEARTS, 7, true)),
                listOf(Card(Suit.SPADES, 8, true)),
                emptyList(),
                emptyList(),
            ),
            drawCount = 1,
            score = 20,
        )

        // 1. Waste -> Tableau awards wasteToTableauPoints (+12) => 32.
        val s1 = GameRules.move(baseState, Pile.Waste, 1, Pile.Tableau(1), config)!!
        assertEquals(32, s1.score)

        // 2. Tableau -> Foundation WITH flip awards toFoundationPoints (+25) + flipTableauCardPoints (+17) = +42 => 74.
        val s2 = GameRules.move(s1, Pile.Tableau(0), 1, Pile.Foundation(Suit.DIAMONDS.ordinal), config)!!
        assertEquals(74, s2.score)
        assertTrue(s2.tableau[0].last().faceUp)

        // 3. Tableau -> Tableau WITH flip awards flipTableauCardPoints (+17) => 91 (5♣ onto 6♥).
        val s3 = GameRules.move(s2, Pile.Tableau(2), 1, Pile.Tableau(1), config)!!
        assertEquals(91, s3.score)
        assertTrue(s3.tableau[2].last().faceUp)

        // 4. Tableau -> Tableau WITHOUT flip awards 0 => 91 (7♥ off face-up 8♣ onto 8♠).
        val s4 = GameRules.move(s3, Pile.Tableau(3), 1, Pile.Tableau(4), config)!!
        assertEquals(91, s4.score)

        // 5. Foundation -> Tableau applies fromFoundationPenalty (-30) and clamps at minScore (10).
        val setupPullBack = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 2, true), Card(Suit.HEARTS, 4, true)),
            foundations = listOf(listOf(Card(Suit.SPADES, 1, true)), emptyList(), emptyList(), emptyList()),
            tableau = listOf(listOf(Card(Suit.HEARTS, 2, true))) + List(6) { emptyList() },
            drawCount = 1,
            score = 50,
        )
        // Move A♠ from Foundation(0) onto 2♥ on Tableau(0) -> -30 => 20.
        val afterPull = GameRules.move(setupPullBack, Pile.Foundation(0), 0, Pile.Tableau(0), config)!!
        assertEquals(20, afterPull.score)
        // Floor clamping at minScore = 10 on low score.
        val lowScoreState = setupPullBack.copy(score = 15)
        val clampedPull = GameRules.move(lowScoreState, Pile.Foundation(0), 0, Pile.Tableau(0), config)!!
        assertEquals(10, clampedPull.score)

        // 6. Waste -> Foundation awards toFoundationPoints (+25).
        val afterWasteToFnd = GameRules.move(
            setupPullBack.copy(waste = listOf(Card(Suit.SPADES, 2, true)), score = 30),
            Pile.Waste,
            0,
            Pile.Foundation(0),
            config,
        )!!
        assertEquals(55, afterWasteToFnd.score)

        // 7. Recycle penalties in Draw-1 and Draw-3, including minScore clamping and applyPenalty = false.
        val draw1Recycle = GameState(emptyList(), listOf(Card(Suit.CLUBS, 5, true)), List(4) { emptyList() }, List(7) { emptyList() }, 1, score = 200)
        assertEquals(50, GameRules.draw(draw1Recycle, applyPenalty = true, config = config)!!.score)
        assertEquals(10, GameRules.draw(draw1Recycle.copy(score = 100), applyPenalty = true, config = config)!!.score)
        assertEquals(200, GameRules.draw(draw1Recycle, applyPenalty = false, config = config)!!.score)

        val draw3Recycle = draw1Recycle.copy(drawCount = 3, score = 100)
        assertEquals(55, GameRules.draw(draw3Recycle, applyPenalty = true, config = config)!!.score)
        assertEquals(10, GameRules.draw(draw3Recycle.copy(score = 30), applyPenalty = true, config = config)!!.score)
    }
}
