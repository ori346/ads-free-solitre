package com.adfree.solitaire.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.adfree.solitaire.config.TapHeuristicConfig
import com.adfree.solitaire.ui.board.DragInfo
import com.adfree.solitaire.ui.board.findDropTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapAndScoringTest {
    @Test fun foundationOutranksTableauWhenTappingTopCardEvenIfStackCanMove() {
        // Col 0 has [hidden, 8♠, 7♥]. Hearts foundation has A..6♥ (so 7♥ can promote).
        // Col 1 has [9♦] (so [8♠, 7♥] could move to reveal hidden).
        // Tapping top card 7♥ (index 2) must prioritize Foundation over tableau move.
        val heartsFoundation = (1..6).map { Card(Suit.HEARTS, it, true) }
        val tableaus = listOf(
            listOf(Card(Suit.CLUBS, 2, false), Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true)),
            listOf(Card(Suit.DIAMONDS, 9, true)),
        ) + List(5) { emptyList() }
        val state = GameState(emptyList(), emptyList(), listOf(emptyList(), heartsFoundation, emptyList(), emptyList()), tableaus, 1)

        val tapTop = Engine.bestTapMove(state, Pile.Tableau(0), 2)
        assertEquals(2 to Pile.Foundation(Suit.HEARTS.ordinal), tapTop)

        // Intentionally tapping higher up the run at 8♠ (index 1) honors explicit structural progress.
        val tapStack = Engine.bestTapMove(state, Pile.Tableau(0), 1)
        assertEquals(1 to Pile.Tableau(1), tapStack)
    }

    @Test fun explicitStackTapWithoutStructuralProgressStillFallsBackToFoundation() {
        // Col 0 has [9♥, 8♠, 7♥] as a valid alternating-color face-up run (9♥ cannot go to foundation yet).
        // Moving [8♠, 7♥] onto [9♦] in Col 1 is a lateral parent swap (no structural progress).
        // Even tapping 8♠ (index 1) must fall back to sending 7♥ to Hearts foundation.
        val heartsFoundation = (1..6).map { Card(Suit.HEARTS, it, true) }
        val tableaus = listOf(
            listOf(Card(Suit.HEARTS, 9, true), Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true)),
            listOf(Card(Suit.DIAMONDS, 9, true)),
        ) + List(5) { emptyList() }
        val state = GameState(emptyList(), emptyList(), listOf(emptyList(), heartsFoundation, emptyList(), emptyList()), tableaus, 1)

        val resolved = Engine.bestTapMove(state, Pile.Tableau(0), 1)
        assertEquals(2 to Pile.Foundation(Suit.HEARTS.ordinal), resolved)
    }

    @Test fun scoringPenalizesBuryingImmediateAndNextFoundationCards() {
        // Waste has 7♠.
        // Diamonds foundation has 6♦ (so 7♦ is next needed, 8♦ has gap == 2).
        // Hearts foundation is empty (8♥ has gap == 8).
        val diamondsFoundation = (1..6).map { Card(Suit.DIAMONDS, it, true) }
        val foundations = listOf(emptyList(), emptyList(), diamondsFoundation, emptyList())

        // Case 1: 7♦ is unexposed (not on top of waste/tableau).
        val unexposedTableaus = listOf(
            listOf(Card(Suit.DIAMONDS, 8, true)), // Col 0: gap == 2, 7♦ unexposed
            listOf(Card(Suit.HEARTS, 8, true)), // Col 1: gap == 8
        ) + List(5) { emptyList() }
        val stateUnexposed = GameState(emptyList(), listOf(Card(Suit.SPADES, 7, true)), foundations, unexposedTableaus, 1)

        val scoreGap2Unexposed = TapTargetScorer.scoreTableauTarget(stateUnexposed, Pile.Waste, 0, Pile.Tableau(0))
        val scoreLargeGap = TapTargetScorer.scoreTableauTarget(stateUnexposed, Pile.Waste, 0, Pile.Tableau(1))
        assertTrue("Large gap should outscore burying gap==2 unexposed", scoreLargeGap > scoreGap2Unexposed)
        assertEquals(0 to Pile.Tableau(1), Engine.bestTapMove(stateUnexposed, Pile.Waste, 0))

        // Case 2: 7♦ IS exposed on Col 2 top -> penalty on Col 0 must be strictly harsher!
        val exposedTableaus = listOf(
            listOf(Card(Suit.DIAMONDS, 8, true)), // Col 0: gap == 2, 7♦ exposed!
            listOf(Card(Suit.HEARTS, 8, true)), // Col 1: gap == 8
            listOf(Card(Suit.DIAMONDS, 7, true)), // Col 2: exposes 7♦
        ) + List(4) { emptyList() }
        val stateExposed = GameState(emptyList(), listOf(Card(Suit.SPADES, 7, true)), foundations, exposedTableaus, 1)
        val scoreGap2Exposed = TapTargetScorer.scoreTableauTarget(stateExposed, Pile.Waste, 0, Pile.Tableau(0))
        assertTrue(
            "Burying gap==2 when next card is exposed ($scoreGap2Exposed) must score lower than unexposed ($scoreGap2Unexposed)",
            scoreGap2Exposed < scoreGap2Unexposed,
        )

        // Case 3: Immediate foundation card (gap == 1) scores even lower than exposed gap == 2.
        val with7DOnFoundation = (1..7).map { Card(Suit.DIAMONDS, it, true) }
        val immediateState = stateUnexposed.copy(
            foundations = listOf(emptyList(), emptyList(), with7DOnFoundation, emptyList()),
        )
        val scoreGap1 = TapTargetScorer.scoreTableauTarget(immediateState, Pile.Waste, 0, Pile.Tableau(0))
        assertTrue(
            "Burying immediate foundation card ($scoreGap1) must score lower than exposed gap==2 ($scoreGap2Exposed)",
            scoreGap1 < scoreGap2Exposed,
        )
    }

    @Test fun scoringPenalizesBuryingDeeperFaceUpFoundationPlayableCardInTargetColumn() {
        // Both Col 0 and Col 1 have 3 cards with 3 face-up cards and identical top rank/color safety.
        // Col 0 has A♣ face-up underneath [K♠, 9♦, 8♥] -> A♣ is immediately foundation-playable!
        // Col 1 has 2♠ face-up underneath [K♣, 9♥, 8♦] -> no buried foundation-playable card.
        val tableaus = listOf(
            listOf(Card(Suit.CLUBS, 1, true), Card(Suit.DIAMONDS, 9, true), Card(Suit.HEARTS, 8, true)),
            listOf(Card(Suit.CLUBS, 13, true), Card(Suit.HEARTS, 9, true), Card(Suit.DIAMONDS, 8, true)),
        ) + List(5) { emptyList() }
        val state = GameState(emptyList(), listOf(Card(Suit.SPADES, 7, true)), List(4) { emptyList() }, tableaus, 1)

        val scoreWithBuriedAce = TapTargetScorer.scoreTableauTarget(state, Pile.Waste, 0, Pile.Tableau(0))
        val scoreClean = TapTargetScorer.scoreTableauTarget(state, Pile.Waste, 0, Pile.Tableau(1))
        assertTrue(
            "Target burying deeper playable foundation card ($scoreWithBuriedAce) must score lower than clean target ($scoreClean)",
            scoreWithBuriedAce < scoreClean,
        )
        assertEquals(0 to Pile.Tableau(1), Engine.bestTapMove(state, Pile.Waste, 0))
    }

    @Test fun fullStackProgressBeatsLateralSplitEvenWhenTappingBottomCard() {
        // Case A: Reveals hidden card.
        val hiddenState = GameState(
            stock = emptyList(),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.CLUBS, 2, false), Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true)),
                listOf(Card(Suit.CLUBS, 8, true)), // lateral target for 7♥
                listOf(Card(Suit.DIAMONDS, 9, true)), // progress target for [8♠, 7♥]
            ) + List(4) { emptyList() },
            drawCount = 1,
        )
        assertEquals(1 to Pile.Tableau(2), Engine.bestTapMove(hiddenState, Pile.Tableau(0), 2))

        // Case B: Frees an underlying card immediately playable to Foundation.
        val clubsFoundation = listOf(Card(Suit.CLUBS, 1, true))
        val freeFoundationState = GameState(
            stock = emptyList(),
            waste = emptyList(),
            foundations = listOf(emptyList(), emptyList(), emptyList(), clubsFoundation),
            tableau = listOf(
                listOf(Card(Suit.CLUBS, 2, true), Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true)),
                listOf(Card(Suit.CLUBS, 8, true)), // lateral target for 7♥
                listOf(Card(Suit.DIAMONDS, 9, true)), // moves [8♠, 7♥] to free 2♣ for Clubs foundation!
            ) + List(4) { emptyList() },
            drawCount = 1,
        )
        assertEquals(1 to Pile.Tableau(2), Engine.bestTapMove(freeFoundationState, Pile.Tableau(0), 2))
    }

    @Test fun customTapHeuristicConfigOverridesChangeTargetRanking() {
        // Col 0 has 8♦ (immediate foundation card, 1 card total).
        // Col 1 has [K♣, Q♥, J♠, 10♦, 9♣, 8♥] (6 cards total, safe from foundation).
        val diamondsFoundation = (1..7).map { Card(Suit.DIAMONDS, it, true) }
        val state = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 7, true)),
            foundations = listOf(emptyList(), emptyList(), diamondsFoundation, emptyList()),
            tableau = listOf(
                listOf(Card(Suit.DIAMONDS, 8, true)),
                listOf(
                    Card(Suit.CLUBS, 13, true),
                    Card(Suit.HEARTS, 12, true),
                    Card(Suit.SPADES, 11, true),
                    Card(Suit.DIAMONDS, 10, true),
                    Card(Suit.CLUBS, 9, true),
                    Card(Suit.HEARTS, 8, true),
                ),
            ) + List(5) { emptyList() },
            drawCount = 1,
        )

        // With default weights, Col 1 wins because burying immediate foundation card (8♦) is heavily penalized.
        assertEquals(Pile.Tableau(1), TapTargetScorer.rankedTargets(state, Pile.Waste, 0).first())

        // Override config: remove foundation bury penalty and strongly penalize column size.
        val stackCompactnessConfig = TapHeuristicConfig(
            buryImmediateFoundationPenalty = 0,
            columnTotalCardPenalty = 50,
            faceUpRunCardPenalty = 50,
        )
        val customBest = TapTargetScorer.bestTapMove(state, Pile.Waste, 0, config = stackCompactnessConfig)
        assertEquals(0 to Pile.Tableau(0), customBest)
    }

    @Test fun scoringPrefersFewerHiddenCardsAndEstablishedRunsOverRightmostHiddenColumns() {
        // 1. Two lone cards over hidden piles: Col 1 (1 hidden card) vs Col 6 (5 hidden cards).
        val twoHiddenCols = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 7, true)),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                emptyList(),
                listOf(Card(Suit.CLUBS, 2, false), Card(Suit.DIAMONDS, 8, true)),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(
                    Card(Suit.CLUBS, 3, false),
                    Card(Suit.CLUBS, 4, false),
                    Card(Suit.CLUBS, 5, false),
                    Card(Suit.CLUBS, 6, false),
                    Card(Suit.CLUBS, 10, false),
                    Card(Suit.HEARTS, 8, true),
                ),
            ),
            drawCount = 1,
        )
        assertEquals(0 to Pile.Tableau(1), Engine.bestTapMove(twoHiddenCols, Pile.Waste, 0))

        // 2. Established run on the left (Col 1: [hidden, 9♣, 8♦]) vs lone card over hidden pile on the right (Col 5: [hidden, 8♥]).
        val runVsLoneHidden = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 7, true)),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                emptyList(),
                listOf(Card(Suit.CLUBS, 2, false), Card(Suit.CLUBS, 9, true), Card(Suit.DIAMONDS, 8, true)),
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(Card(Suit.CLUBS, 3, false), Card(Suit.HEARTS, 8, true)),
                emptyList(),
            ),
            drawCount = 1,
        )
        assertEquals(0 to Pile.Tableau(1), Engine.bestTapMove(runVsLoneHidden, Pile.Waste, 0))
    }

    @Test fun tapCycleTrackerCyclesAllTargetsWrapsCleanlyAndResetsOnDifferentCard() {
        // 7♠ on Waste has 3 destinations: Foundation(0), Tableau(3) [8♥], Tableau(1) [hidden, 8♦].
        // Also place 5♣ on Col 4 to test resetting when tapping a different card.
        val spadesFoundation = (1..6).map { Card(Suit.SPADES, it, true) }
        val diamondsFoundation = (1..5).map { Card(Suit.DIAMONDS, it, true) }
        val initial = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 7, true)),
            foundations = listOf(spadesFoundation, emptyList(), diamondsFoundation, emptyList()),
            tableau = listOf(
                emptyList(),
                listOf(Card(Suit.CLUBS, 2, false), Card(Suit.DIAMONDS, 8, true)),
                emptyList(),
                listOf(Card(Suit.HEARTS, 8, true)),
                listOf(Card(Suit.CLUBS, 5, true)),
                emptyList(),
                listOf(Card(Suit.DIAMONDS, 6, true)),
            ),
            drawCount = 1,
        )

        val tracker = TapCycleTracker()
        var curr = initial
        var pile: Pile = Pile.Waste
        var idx = 0

        val visitedTargets = mutableListOf<Pile>()
        repeat(6) { step ->
            val resolved = tracker.resolveTapMove(curr, pile, idx)
            assertNotNull("Expected legal move on tap #$step", resolved)
            val (startIdx, target) = resolved!!
            val next = Engine.move(curr, pile, startIdx, target)!!
            tracker.recordMove(curr, pile, startIdx, target)
            visitedTargets.add(target)
            curr = next
            pile = target
            idx = curr.pile(target).lastIndex
        }

        // First 3 taps must hit all 3 distinct destinations without skipping any.
        val expectedDistinct = setOf<Pile>(Pile.Foundation(0), Pile.Tableau(1), Pile.Tableau(3))
        assertEquals(expectedDistinct, visitedTargets.take(3).toSet())
        // 4th tap must wrap cleanly back to the #1 destination (Foundation(0)), and taps 4..6 repeat a full cycle.
        assertEquals(visitedTargets[0], visitedTargets[3])
        assertEquals(expectedDistinct, visitedTargets.drop(3).toSet())

        // Start a fresh partial cycle on 7♠ (currently at Tableau(1)):
        // Tap A -> Foundation(0), Tap B -> Tableau(3). Without a reset, next tap on 7♠ at Tableau(3)
        // would avoid Foundation(0) and move to Tableau(1).
        val resetTracker = TapCycleTracker()
        val a = resetTracker.resolveTapMove(curr, pile, idx)!!
        assertEquals(Pile.Foundation(0), a.second)
        val afterA = Engine.move(curr, pile, a.first, a.second)!!
        resetTracker.recordMove(curr, pile, a.first, a.second)

        val b = resetTracker.resolveTapMove(afterA, Pile.Foundation(0), afterA.pile(Pile.Foundation(0)).lastIndex)!!
        assertEquals(Pile.Tableau(3), b.second)
        val afterB = Engine.move(afterA, Pile.Foundation(0), b.first, b.second)!!
        resetTracker.recordMove(afterA, Pile.Foundation(0), b.first, b.second)

        // Tap a completely different card (5♣ on Tableau(4) -> Tableau(6)) -> resets cycle for 7♠!
        val otherMove = resetTracker.resolveTapMove(afterB, Pile.Tableau(4), 0)!!
        assertEquals(0 to Pile.Tableau(6), otherMove)
        val afterOther = Engine.move(afterB, Pile.Tableau(4), otherMove.first, otherMove.second)!!
        resetTracker.recordMove(afterB, Pile.Tableau(4), otherMove.first, otherMove.second)

        // Tap 7♠ again at Tableau(3): since cycle was reset, it chooses Foundation(0) instead of Tableau(1)!
        val backToSeven = resetTracker.resolveTapMove(afterOther, Pile.Tableau(3), afterOther.pile(Pile.Tableau(3)).lastIndex)!!
        assertNotEquals(Pile.Tableau(1), backToSeven.second)
        assertEquals(Pile.Foundation(0), backToSeven.second)
    }

    @Test fun tapCycleTrackerCyclesMultiCardRunAndKeepsRootKingAnchored() {
        // Multi-card run [8♠, 7♥] in Col 0 can move to Col 2 ([9♦]) or Col 5 ([9♥]).
        val runState = GameState(
            stock = emptyList(),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                listOf(Card(Suit.SPADES, 8, true), Card(Suit.HEARTS, 7, true)),
                emptyList(),
                listOf(Card(Suit.DIAMONDS, 9, true)),
                emptyList(),
                emptyList(),
                listOf(Card(Suit.HEARTS, 9, true)),
                emptyList(),
            ),
            drawCount = 1,
        )
        val tracker = TapCycleTracker()
        val firstMove = tracker.resolveTapMove(runState, Pile.Tableau(0), 0)!!
        assertEquals(0 to Pile.Tableau(2), firstMove)
        val afterFirst = Engine.move(runState, Pile.Tableau(0), firstMove.first, firstMove.second)!!
        tracker.recordMove(runState, Pile.Tableau(0), firstMove.first, firstMove.second)

        val secondMove = tracker.resolveTapMove(afterFirst, Pile.Tableau(2), 1)!!
        assertEquals(1 to Pile.Tableau(5), secondMove)
        val afterSecond = Engine.move(afterFirst, Pile.Tableau(2), secondMove.first, secondMove.second)!!
        tracker.recordMove(afterFirst, Pile.Tableau(2), secondMove.first, secondMove.second)

        // 3rd tap wraps cleanly back to Col 2 ([9♦]).
        val thirdMove = tracker.resolveTapMove(afterSecond, Pile.Tableau(5), 1)!!
        assertEquals(1 to Pile.Tableau(2), thirdMove)

        // A King on Waste moves to an empty column, and once seated at index 0 it stays anchored
        // rather than bouncing endlessly across other empty columns.
        val kingState = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 13, true)),
            foundations = List(4) { emptyList() },
            tableau = listOf(
                emptyList(),
                emptyList(),
                listOf(Card(Suit.CLUBS, 4, true)),
                emptyList(),
                emptyList(),
                listOf(Card(Suit.HEARTS, 9, true)),
                listOf(Card(Suit.DIAMONDS, 2, true)),
            ),
            drawCount = 1,
        )
        val kingMove = Engine.bestTapMove(kingState, Pile.Waste, 0)!!
        assertEquals(0 to Pile.Tableau(0), kingMove)
        val afterKing = Engine.move(kingState, Pile.Waste, kingMove.first, kingMove.second)!!
        assertNull(Engine.bestTapMove(afterKing, Pile.Tableau(0), 0))
    }

    @Test fun findDropTargetReturnsNullWhenCardDraggedBackToOriginalStackDespiteAdjacentLegalTargets() {
        val cardW = 140f
        val cardH = 203f
        val gap = 6f
        val tableauTop = 220f
        val tableauBottom = 1600f
        val marginFraction = 0.45f

        val pileBounds = buildMap<Pile, Rect> {
            for (i in 0 until 4) {
                val left = i * (cardW + gap)
                put(Pile.Foundation(i), Rect(left, 0f, left + cardW, cardH))
            }
            val wasteLeft = 4 * (cardW + gap)
            put(Pile.Waste, Rect(wasteLeft, 0f, wasteLeft + cardW * 2 + gap, cardH))
            for (col in 0 until 7) {
                val left = col * (cardW + gap)
                put(Pile.Tableau(col), Rect(left, tableauTop, left + cardW, tableauBottom))
            }
        }

        // 7♥ in Tableau(1) can legally move to Tableau(0) (8♠), Tableau(2) (8♣), AND Foundation(1) (A..6♥).
        val heartsFoundation = (1..6).map { Card(Suit.HEARTS, it, true) }
        val sevenHearts = Card(Suit.HEARTS, 7, true)
        val state = GameState(
            stock = emptyList(),
            waste = listOf(Card(Suit.SPADES, 7, true)),
            foundations = listOf(emptyList(), heartsFoundation, emptyList(), emptyList()),
            tableau = listOf(
                listOf(Card(Suit.SPADES, 8, true)),
                listOf(sevenHearts),
                listOf(Card(Suit.CLUBS, 8, true)),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            ),
            drawCount = 1,
        )

        val col1Origin = Offset(1 * (cardW + gap), tableauTop)
        fun dragAt(offset: Offset) = DragInfo(
            from = Pile.Tableau(1),
            index = 0,
            cards = listOf(sevenHearts),
            ys = listOf(0f),
            origin = col1Origin,
            offset = offset,
        )

        // 1. Dragged out to Tableau(2): resolves to Tableau(2).
        assertEquals(
            Pile.Tableau(2),
            findDropTarget(dragAt(Offset(cardW + gap, 0f)), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )

        // 2. Dragged back to dead center of original stack Tableau(1): must return null (cancel drag).
        assertNull(
            findDropTarget(dragAt(Offset.Zero), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )

        // 3. Dragged back near left or right edge of original stack Tableau(1) (within neighbor's 0.45 margin):
        // must still return null because center is inside Tableau(1)!
        assertNull(
            findDropTarget(dragAt(Offset(-cardW * 0.40f, 0f)), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )
        assertNull(
            findDropTarget(dragAt(Offset(cardW * 0.40f, 0f)), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )

        // 4. Dragged back near top of original stack Tableau(1) (within Foundation(1)'s 0.45 vertical margin):
        // must still return null while card center remains in Tableau(1)!
        assertNull(
            findDropTarget(dragAt(Offset(0f, -cardH * 0.35f)), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )

        // 5. Dragged up onto Foundation(1): resolves to Foundation(1).
        assertEquals(
            Pile.Foundation(1),
            findDropTarget(dragAt(Offset(0f, -tableauTop)), state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )

        // 6. Dragged from Waste and returned back onto Waste origin: must return null even if Tableau below is legal.
        val wasteOrigin = Offset(5 * (cardW + gap), 0f)
        val wasteDragReturned = DragInfo(
            from = Pile.Waste,
            index = 0,
            cards = state.waste,
            ys = listOf(0f),
            origin = wasteOrigin,
            offset = Offset(-cardW * 0.3f, cardH * 0.2f),
        )
        assertNull(
            findDropTarget(wasteDragReturned, state, pileBounds, Offset.Zero, cardW, cardH, marginFraction),
        )
    }
}
