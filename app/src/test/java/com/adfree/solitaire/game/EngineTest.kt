package com.adfree.solitaire.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    @Test fun dealIsCorrect() {
        val s = Engine.newGame(1, seed = 42)
        assertEquals(24, s.stock.size)
        s.tableau.forEachIndexed { i, col ->
            assertEquals(i + 1, col.size)
            assertTrue(col.last().faceUp)
            assertTrue(col.dropLast(1).none { it.faceUp })
        }
        val all = s.stock + s.tableau.flatten()
        assertEquals(52, all.map { it.id }.toSet().size)
    }

    @Test fun drawAndRecycle() {
        var s = Engine.newGame(3, seed = 1)
        repeat(8) { s = Engine.draw(s)!! }
        assertEquals(0, s.stock.size)
        assertEquals(24, s.waste.size)
        val firstDrawnTop = s.waste[2]
        s = Engine.draw(s)!!
        assertEquals(24, s.stock.size)
        s = Engine.draw(s)!!
        assertEquals(firstDrawnTop.id, s.waste.last().id)
    }

    @Test fun placementRules() {
        val redSeven = Card(Suit.HEARTS, 7, true)
        assertTrue(Engine.canPlaceOnTableau(listOf(Card(Suit.SPADES, 8, true)), redSeven))
        assertFalse(Engine.canPlaceOnTableau(listOf(Card(Suit.DIAMONDS, 8, true)), redSeven))
        assertTrue(Engine.canPlaceOnTableau(emptyList(), Card(Suit.CLUBS, 13, true)))
        assertTrue(Engine.canPlaceOnFoundation(emptyList(), Card(Suit.CLUBS, 1, true)))
        assertFalse(Engine.canPlaceOnFoundation(listOf(Card(Suit.CLUBS, 1, true)), Card(Suit.SPADES, 2, true)))
    }

    @Test fun autoCompleteWins() {
        // All cards face-up in tableau, sorted so auto-complete can finish.
        val cols = List(7) { mutableListOf<Card>() }
        var k = 0
        for (rank in 13 downTo 1) for (suit in Suit.entries) cols[k++ % 7].add(Card(suit, rank, true))
        var s = GameState(emptyList(), emptyList(), List(4) { emptyList() }, cols, 1)
        assertTrue(Engine.canAutoComplete(s))
        while (!s.isWon) s = Engine.autoCompleteStep(s)!!
        assertTrue(s.isWon)
    }

    @Test fun autoCompleteWithWasteAndStock() {
        // Cards split between tableau, waste, and stock in draw-1 and draw-3 modes.
        val cols = List(7) { mutableListOf<Card>() }
        val stock = mutableListOf<Card>()
        val waste = mutableListOf<Card>()
        var k = 0
        for (rank in 13 downTo 3) for (suit in Suit.entries) cols[k++ % 7].add(Card(suit, rank, true))
        // Put Aces and 2s in waste and stock
        for (suit in Suit.entries) {
            waste.add(Card(suit, 1, true))
            stock.add(Card(suit, 2, false))
        }
        var s = GameState(stock, waste, List(4) { emptyList() }, cols, 3)
        assertTrue(Engine.canAutoComplete(s))
        var steps = 0
        while (!s.isWon && steps++ < 200) s = Engine.autoCompleteStep(s)!!
        assertTrue(s.isWon)
    }

    @Test fun hintExistsOnFreshDeal() {
        assertNotNull(Engine.findHint(Engine.newGame(1, seed = 7)))
    }

    @Test fun tapBottomOfRunMovesWholeStackWhenNeeded() {
        // Col 0 has [8♠, 7♥] face-up; Col 1 has [9♦] face-up.
        // Tapping 7♥ (index 1) has no single-card move, so bestTapMove should move the [8♠, 7♥] stack (index 0) to Col 1.
        val col0 = listOf(Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true))
        val col1 = listOf(Card(Suit.DIAMONDS, 9, true))
        val tableaus = listOf(col0, col1) + List(5) { emptyList() }
        val s = GameState(emptyList(), emptyList(), List(4) { emptyList() }, tableaus, 1)
        val resolved = Engine.bestTapMove(s, Pile.Tableau(0), 1)
        assertNotNull(resolved)
        assertEquals(0, resolved!!.first)
        assertEquals(Pile.Tableau(1), resolved.second)
    }

    @Test fun tapMiddleOfStackMovesTopExposedCardToFoundation() {
        // Foundation 3 (Clubs) has A♣, 2♣, 3♣.
        // Col 0 has [6♣, 5♦, 4♣] face-up.
        // Pressing in the middle of the stack (e.g. index 0 or 1) should move 4♣ (index 2) to the Clubs foundation!
        val clubsFoundation = listOf(
            Card(Suit.CLUBS, 1, true),
            Card(Suit.CLUBS, 2, true),
            Card(Suit.CLUBS, 3, true),
        )
        val foundations = listOf(emptyList(), emptyList(), emptyList(), clubsFoundation)
        val col0 = listOf(
            Card(Suit.CLUBS, 6, true),
            Card(Suit.DIAMONDS, 5, true),
            Card(Suit.CLUBS, 4, true),
        )
        val tableaus = listOf(col0) + List(6) { emptyList() }
        val s = GameState(emptyList(), emptyList(), foundations, tableaus, 1)

        // Tap directly on 4♣ (index 2)
        val directTap = Engine.bestTapMove(s, Pile.Tableau(0), 2)
        assertNotNull(directTap)
        assertEquals(2, directTap!!.first)
        assertEquals(Pile.Foundation(3), directTap.second)

        // Tap in the middle of the stack (5♦ at index 1 or 6♣ at index 0)
        val middleTap = Engine.bestTapMove(s, Pile.Tableau(0), 1)
        assertNotNull(middleTap)
        assertEquals(2, middleTap!!.first)
        assertEquals(Pile.Foundation(3), middleTap.second)
    }

    @Test fun tapMiddleOfStackMovesTopExposedCardToTableauWhenStackCannotMove() {
        // Col 0 has [8♠, 7♥, 6♣] face-up.
        // Col 1 has [7♦] face-up (so 6♣ can move onto 7♦, but [8♠, 7♥, 6♣] and [7♥, 6♣] have no move).
        // Tapping in the middle of Col 0 (index 1, 7♥) should move the exposed top card (6♣ at index 2) to Col 1!
        val col0 = listOf(
            Card(Suit.SPADES, 8, true),
            Card(Suit.HEARTS, 7, true),
            Card(Suit.CLUBS, 6, true),
        )
        val col1 = listOf(Card(Suit.DIAMONDS, 7, true))
        val tableaus = listOf(col0, col1) + List(5) { emptyList() }
        val s = GameState(emptyList(), emptyList(), List(4) { emptyList() }, tableaus, 1)

        val resolved = Engine.bestTapMove(s, Pile.Tableau(0), 1)
        assertNotNull(resolved)
        assertEquals(2, resolved!!.first)
        assertEquals(Pile.Tableau(1), resolved.second)
    }

    @Test fun aceMovesToMatchingSuitFoundationSlot() {
        // Tapping A♣ should place it on Foundation(3) (the ♣ slot), not Foundation(0) (the ♠ slot).
        val col0 = listOf(Card(Suit.CLUBS, 1, true))
        val tableaus = listOf(col0) + List(6) { emptyList() }
        val s = GameState(emptyList(), emptyList(), List(4) { emptyList() }, tableaus, 1)
        val resolved = Engine.bestTapMove(s, Pile.Tableau(0), 0)
        assertNotNull(resolved)
        assertEquals(0, resolved!!.first)
        assertEquals(Pile.Foundation(Suit.CLUBS.ordinal), resolved.second)
    }

    @Test fun secureRandomDealsAreDistinct() {
        val g1 = Engine.newGame(1)
        val g2 = Engine.newGame(1)
        val ids1 = (g1.stock + g1.tableau.flatten()).map { it.id }
        val ids2 = (g2.stock + g2.tableau.flatten()).map { it.id }
        assertEquals(52, ids1.toSet().size)
        assertEquals(52, ids2.toSet().size)
        assertFalse(ids1 == ids2)
    }

    private fun fnd(s: GameState) = s.foundations.sumOf { it.size }

    /** Aces on the tableau; everything else in the stock (face-down), tableau otherwise face-up. */
    private fun bigStockState(drawCount: Int): GameState {
        val order = Suit.entries.flatMap { su -> (2..13).map { Card(su, it, false) } }
        // Bury low cards deep so recycling is needed.
        val stock = order.sortedByDescending { it.rank }.reversed().reversed()
        val cols = List(7) { emptyList<Card>() }.toMutableList()
        Suit.entries.forEachIndexed { i, su -> cols[i] = listOf(Card(su, 1, true)) }
        return GameState(stock, emptyList(), List(4) { emptyList() }, cols, drawCount)
    }

    @Test fun autoCompleteNeverReducesScore() {
        for (dc in listOf(1, 3)) {
            var s = bigStockState(dc).copy(score = 500)
            var steps = 0
            while (!s.isWon && steps++ < 5000) {
                val n = Engine.autoCompleteStep(s) ?: break
                assertTrue(n.score >= s.score)
                s = n
            }
            assertTrue(s.isWon)
        }
    }

    @Test fun autoCompleteFromDraw1LargeStock() {
        val s = bigStockState(1)
        assertTrue(Engine.canAutoComplete(s))
        val end = Engine.simulateAutoComplete(s)
        assertNotNull(end)
        assertTrue(end!!.isWon)
        assertTrue(end.score >= s.score)
    }

    @Test fun canAutoCompleteDraw3Correctness() {
        val s = bigStockState(3)
        assertTrue(Engine.canAutoComplete(s))
        assertTrue(Engine.simulateAutoComplete(s)!!.isWon)
        // Unsolvable: a 2♠ is buried under its own... king of spades is missing from play (only stuck cards).
        val stuck = GameState(
            emptyList(),
            listOf(Card(Suit.SPADES, 5, true), Card(Suit.HEARTS, 5, true)),
            List(4) { emptyList() },
            List(7) { emptyList() },
            3,
        )
        assertFalse(Engine.canAutoComplete(stuck))
    }

    /** Deadlocked tableau setup where no tableau-to-tableau or tableau-to-foundation moves exist. */
    private fun deadlockedTableaus(): List<List<Card>> {
        // Black even cards and a black King: no moves between each other, no moves to foundation (empty).
        return listOf(
            listOf(Card(Suit.SPADES, 2, true)),
            listOf(Card(Suit.SPADES, 4, true)),
            listOf(Card(Suit.SPADES, 6, true)),
            listOf(Card(Suit.SPADES, 8, true)),
            listOf(Card(Suit.SPADES, 10, true)),
            listOf(Card(Suit.SPADES, 12, true)),
            listOf(Card(Suit.CLUBS, 13, true)),
        )
    }

    @Test fun findHintReturnsNullWhenStockCycleHasNoPlayableMovesDraw1() {
        val tableaus = deadlockedTableaus()
        val stock = listOf(
            Card(Suit.HEARTS, 10, false),
            Card(Suit.DIAMONDS, 10, false),
        )
        val waste = listOf(Card(Suit.CLUBS, 10, true))
        val s = GameState(stock, waste, List(4) { emptyList() }, tableaus, 1)

        assertFalse(Engine.canStockCycleProduceMove(s))
        assertNull(Engine.findHint(s))
    }

    @Test fun findHintReturnsNullWhenStockCycleHasNoPlayableMovesDraw3() {
        val tableaus = deadlockedTableaus()
        val stock = listOf(
            Card(Suit.HEARTS, 10, false),
            Card(Suit.DIAMONDS, 10, false),
            Card(Suit.SPADES, 10, false),
            Card(Suit.HEARTS, 6, false),
            Card(Suit.DIAMONDS, 6, false),
            Card(Suit.SPADES, 6, false),
        )
        val waste = listOf(Card(Suit.CLUBS, 10, true))
        val s = GameState(stock, waste, List(4) { emptyList() }, tableaus, 3)

        assertFalse(Engine.canStockCycleProduceMove(s))
        assertNull(Engine.findHint(s))
    }

    @Test fun findHintReturnsStockHintWhenPlayableCardInStock() {
        val tableaus = deadlockedTableaus()
        // Stock has an Ace of Spades which can move to Foundation(0)
        val stock = listOf(
            Card(Suit.HEARTS, 10, false),
            Card(Suit.SPADES, 1, false),
        )
        val waste = listOf(Card(Suit.CLUBS, 10, true))
        val s = GameState(stock, waste, List(4) { emptyList() }, tableaus, 1)

        assertTrue(Engine.canStockCycleProduceMove(s))
        val hint = Engine.findHint(s)
        assertNotNull(hint)
        assertEquals(Hint(Pile.Stock, 0, Pile.Waste), hint)
    }

    @Test fun findHintReturnsStockHintWhenPlayableCardReachableAfterRecycle() {
        val tableaus = deadlockedTableaus()
        // Stock is empty, but recycling waste reveals an Ace of Spades
        val waste = listOf(
            Card(Suit.SPADES, 1, true),
            Card(Suit.HEARTS, 10, true),
        )
        val s = GameState(emptyList(), waste, List(4) { emptyList() }, tableaus, 1)

        assertTrue(Engine.canStockCycleProduceMove(s))
        val hint = Engine.findHint(s)
        assertNotNull(hint)
        assertEquals(Hint(Pile.Stock, 0, Pile.Waste), hint)
    }

    @Test fun findHintReturnsStockHintWhenTableauMoveBecomesAvailableFromStockDraw3() {
        // Col 0 has 8♠. In draw 3, 7♥ is dealt to the top of waste.
        val col0 = listOf(Card(Suit.SPADES, 8, true))
        val tableaus = listOf(col0) + List(6) { emptyList() }
        // When drawing 3, the bottom-most of the 3 cards (index 0) becomes the top card of waste after reversal.
        val stock = listOf(
            Card(Suit.HEARTS, 7, false),
            Card(Suit.DIAMONDS, 4, false),
            Card(Suit.CLUBS, 4, false),
        )
        val s = GameState(stock, emptyList(), List(4) { emptyList() }, tableaus, 3)

        assertTrue(Engine.canStockCycleProduceMove(s))
        val hint = Engine.findHint(s)
        assertNotNull(hint)
        assertEquals(Hint(Pile.Stock, 0, Pile.Waste), hint)
    }

    @Test fun findHintReturnsStockHintWhenPlayableCardInWasteDraw1RequiresFullCycle() {
        val col0 = listOf(Card(Suit.SPADES, 8, true))
        val tableaus = listOf(col0) + List(6) { emptyList() }
        // 20 unplayable distinct cards in stock (no Aces, no Kings, and no Red 7s)
        val stock = listOf(
            Card(Suit.SPADES, 2, false),
            Card(Suit.CLUBS, 2, false),
            Card(Suit.HEARTS, 2, false),
            Card(Suit.DIAMONDS, 2, false),
            Card(Suit.SPADES, 3, false),
            Card(Suit.CLUBS, 3, false),
            Card(Suit.HEARTS, 3, false),
            Card(Suit.DIAMONDS, 3, false),
            Card(Suit.SPADES, 4, false),
            Card(Suit.CLUBS, 4, false),
            Card(Suit.HEARTS, 4, false),
            Card(Suit.DIAMONDS, 4, false),
            Card(Suit.SPADES, 5, false),
            Card(Suit.CLUBS, 5, false),
            Card(Suit.HEARTS, 5, false),
            Card(Suit.DIAMONDS, 5, false),
            Card(Suit.SPADES, 6, false),
            Card(Suit.CLUBS, 6, false),
            Card(Suit.HEARTS, 6, false),
            Card(Suit.DIAMONDS, 6, false),
        )
        // 4 cards in waste: index 1 has 7♥ (playable onto 8♠), but index 3 is 9♠ (unplayable on top)
        val waste = listOf(
            Card(Suit.CLUBS, 9, true),
            Card(Suit.HEARTS, 7, true),
            Card(Suit.DIAMONDS, 9, true),
            Card(Suit.SPADES, 9, true),
        )
        val s = GameState(stock, waste, List(4) { emptyList() }, tableaus, 1)

        assertTrue(Engine.canStockCycleProduceMove(s))
        val hint = Engine.findHint(s)
        assertNotNull(hint)
        assertEquals(Hint(Pile.Stock, 0, Pile.Waste), hint)
    }

    @Test fun findHintsReturnsMultipleDistinctHintsInPriorityOrder() {
        // Col 0 has A♠ (can go to Foundation 0).
        // Col 1 has a hidden card under 7♥, and Col 2 has 8♠ (so 7♥ can move to Col 2 to reveal hidden card).
        // Waste has 6♣ (can move onto 7♥ in Col 1).
        // Stock has A♥ (playable when drawn).
        val col0 = listOf(Card(Suit.SPADES, 1, true))
        val col1 = listOf(Card(Suit.DIAMONDS, 10, false), Card(Suit.HEARTS, 7, true))
        val col2 = listOf(Card(Suit.SPADES, 8, true))
        val tableaus = listOf(col0, col1, col2) + List(4) { emptyList() }
        val waste = listOf(Card(Suit.CLUBS, 6, true))
        val stock = listOf(Card(Suit.HEARTS, 1, false))
        val s = GameState(stock, waste, List(4) { emptyList() }, tableaus, 1)

        val hints = Engine.findHints(s)
        assertEquals(4, hints.size)
        // 1. Foundation move
        assertEquals(Hint(Pile.Tableau(0), 0, Pile.Foundation(0)), hints[0])
        // 2. Tableau move that reveals a hidden card
        assertEquals(Hint(Pile.Tableau(1), 1, Pile.Tableau(2)), hints[1])
        // 3. Waste to tableau move
        assertEquals(Hint(Pile.Waste, 0, Pile.Tableau(1)), hints[2])
        // 4. Stock draw
        assertEquals(Hint(Pile.Stock, 0, Pile.Waste), hints[3])
    }

    @Test fun omniscientSolverDetectsSolvableAndUnsolvableAndRedraws() {
        // Unsolvable partial/full state: a face-down card in Col 0 under a 2♠, with no playable moves anywhere
        val deadlockedWithHidden = GameState(
            stock = listOf(Card(Suit.HEARTS, 10, false)),
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
        assertFalse(Engine.isSolvableOmniscient(deadlockedWithHidden))

        // Verify that newGame with ensureSolvable=true always returns a solvable deal and invokes callback/debug on unsolvable
        var skipped = 0
        for (seed in 1L..25L) {
            val g = Engine.newGame(
                drawCount = 3,
                random = kotlin.random.Random(seed),
                ensureSolvable = true,
                debug = true,
                onUnsolvableDeal = { _, _ -> skipped++ },
            )
            assertTrue(Engine.isSolvableOmniscient(g))
        }
        assertTrue("Expected at least one unsolvable deal across 25 Draw-3 seeds to be skipped", skipped > 0)
    }

    @Test fun bestTargetAvoidsBuryingCardNeededByFoundation() {
        // Waste has 7♠.
        // Col 0 (leftmost) has 8♦, and Diamonds foundation is at 7♦ (so 8♦ can go to foundation right now!).
        // Col 3 has 8♥, and Hearts foundation is empty.
        // Smart target selection should send 7♠ to Col 3 (8♥) instead of Col 0 (8♦).
        val diamondsFoundation = (1..7).map { Card(Suit.DIAMONDS, it, true) }
        val foundations = listOf(emptyList(), emptyList(), diamondsFoundation, emptyList())
        val tableaus = listOf(
            listOf(Card(Suit.DIAMONDS, 8, true)),
            emptyList(),
            emptyList(),
            listOf(Card(Suit.HEARTS, 8, true)),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val waste = listOf(Card(Suit.SPADES, 7, true))
        val s = GameState(emptyList(), waste, foundations, tableaus, 1)

        val resolved = Engine.bestTapMove(s, Pile.Waste, 0)
        assertNotNull(resolved)
        assertEquals(0 to Pile.Tableau(3), resolved)
    }

    @Test fun bestTargetPrefersEstablishedCleanRunOverHiddenRightColumnAndSingletons() {
        // Waste has 7♠.
        // Col 0 (leftmost) has an established clean King run [K♠, Q♥, J♠, 10♦, 9♣, 8♦] (0 hidden cards).
        // Col 4 has a lone [8♥] (or a deep hidden column).
        // Smart target selection should continue building the clean run in Col 0 rather than jumping to Col 4.
        val tableaus = listOf(
            listOf(
                Card(Suit.SPADES, 13, true),
                Card(Suit.HEARTS, 12, true),
                Card(Suit.SPADES, 11, true),
                Card(Suit.DIAMONDS, 10, true),
                Card(Suit.CLUBS, 9, true),
                Card(Suit.DIAMONDS, 8, true),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(Card(Suit.HEARTS, 8, true)),
            emptyList(),
            emptyList(),
        )
        val waste = listOf(Card(Suit.SPADES, 7, true))
        val s = GameState(emptyList(), waste, List(4) { emptyList() }, tableaus, 1)

        val resolved = Engine.bestTapMove(s, Pile.Waste, 0)
        assertNotNull(resolved)
        assertEquals(0 to Pile.Tableau(0), resolved)
    }

    @Test fun bestTapMovePrefersRevealingHiddenCardOverLateralSubstackSplit() {
        // Col 0 has [hidden, 8♠, 7♥].
        // Col 1 has [8♣] (a lateral split for 7♥ that accomplishes nothing).
        // Col 2 has [9♦] (moving [8♠, 7♥] to 9♦ reveals the hidden card in Col 0!).
        // Even when tapping 7♥ (index 2), bestTapMove should move [8♠, 7♥] (index 1) to Col 2.
        val col0 = listOf(
            Card(Suit.CLUBS, 2, false),
            Card(Suit.SPADES, 8, true),
            Card(Suit.HEARTS, 7, true),
        )
        val col1 = listOf(Card(Suit.CLUBS, 8, true))
        val col2 = listOf(Card(Suit.DIAMONDS, 9, true))
        val tableaus = listOf(col0, col1, col2) + List(4) { emptyList() }
        val s = GameState(emptyList(), emptyList(), List(4) { emptyList() }, tableaus, 1)

        val resolved = Engine.bestTapMove(s, Pile.Tableau(0), 2)
        assertNotNull(resolved)
        assertEquals(1 to Pile.Tableau(2), resolved)
    }

    @Test fun bestTapMoveCyclesAcrossAllAlternativesWithAvoidTargets() {
        // 7♠ can go to Foundation(0) (has 1..6♠), Tableau(3) (8♥), or Tableau(1) (8♦, closer to foundation).
        // Col 1 has 2 cards: [hidden, 8♦] (gap=3 to foundation, but larger stack than Col 3).
        // Col 3 has 1 card: [8♥] (gap=8 to foundation, smaller stack).
        // With smaller stack preference, Col 3 is preferred over Col 1.
        val spadesFoundation = (1..6).map { Card(Suit.SPADES, it, true) }
        val diamondsFoundation = (1..5).map { Card(Suit.DIAMONDS, it, true) }
        val foundations = listOf(spadesFoundation, emptyList(), diamondsFoundation, emptyList())
        val tableaus = listOf(
            emptyList(),
            listOf(Card(Suit.CLUBS, 2, false), Card(Suit.DIAMONDS, 8, true)),
            emptyList(),
            listOf(Card(Suit.HEARTS, 8, true)),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val waste = listOf(Card(Suit.SPADES, 7, true))
        val s0 = GameState(emptyList(), waste, foundations, tableaus, 1)

        // Tap 1 from Waste -> Foundation(0)
        val move1 = Engine.bestTapMove(s0, Pile.Waste, 0)!!
        assertEquals(0 to Pile.Foundation(0), move1)
        val s1 = Engine.move(s0, Pile.Waste, move1.first, move1.second)!!

        // Tap 2 on Foundation(0) with visited={Waste, Foundation(0)} -> Tableau(3) (best tableau target)
        val visited1 = setOf(Pile.Waste, Pile.Foundation(0))
        val move2 = Engine.bestTapMove(s1, Pile.Foundation(0), s1.pile(Pile.Foundation(0)).lastIndex, visited1)!!
        assertEquals(6 to Pile.Tableau(3), move2)
        val s2 = Engine.move(s1, Pile.Foundation(0), move2.first, move2.second)!!

        // Tap 3 on Tableau(3) with visited={Waste, Foundation(0), Tableau(3)} -> Tableau(1) (remaining alternative!)
        val visited2 = visited1 + Pile.Tableau(3)
        val move3 = Engine.bestTapMove(s2, Pile.Tableau(3), s2.pile(Pile.Tableau(3)).lastIndex, visited2)!!
        assertEquals(1 to Pile.Tableau(1), move3)
    }
}
