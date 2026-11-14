package com.adfree.solitaire.data

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.game.Engine
import com.adfree.solitaire.game.GameState
import com.adfree.solitaire.game.Pile
import com.adfree.solitaire.game.Suit
import com.adfree.solitaire.game.UndoRedoHistory
import com.adfree.solitaire.ui.hud.formatScoreDate
import com.adfree.solitaire.ui.hud.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StateManagementTest {
    private fun sampleState(score: Int, moves: Int): GameState =
        Engine.dealRawGame(1, kotlin.random.Random(1L)).copy(score = score, moves = moves)

    @Test fun undoRedoHistoryTracksUndoRedoCapacityAndClearsRedoOnNewPush() {
        val history = UndoRedoHistory<GameState>(maxSize = 3)
        assertTrue(history.isEmpty())
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.firstOrNull())

        val s0 = sampleState(0, 0)
        val s1 = sampleState(10, 1)
        val s2 = sampleState(20, 2)
        val s3 = sampleState(30, 3)
        val s4 = sampleState(40, 4)

        assertNull(history.undo(s0))
        assertNull(history.redo(s0))

        // Record 4 prior states into capacity-3 history -> s0 evicted, retains [s1, s2, s3].
        history.record(s0)
        history.record(s1)
        history.record(s2)
        history.record(s3)

        assertFalse(history.isEmpty())
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(s1, history.firstOrNull())
        assertEquals(listOf(s1, s2, s3), history.toList())

        // Undo from s4 -> s3, then undo -> s2.
        val afterUndo1 = history.undo(s4)
        assertEquals(s3, afterUndo1)
        assertTrue(history.canRedo)
        val afterUndo2 = history.undo(afterUndo1!!)
        assertEquals(s2, afterUndo2)
        assertEquals(listOf(s1), history.toList())

        // Redo restores s3.
        val afterRedo = history.redo(afterUndo2!!)
        assertEquals(s3, afterRedo)
        assertTrue(history.canUndo)
        assertTrue(history.canRedo)

        // Recording a new move after undo/redo clears redo stack.
        val s3Alt = sampleState(35, 3)
        history.record(afterRedo!!)
        assertFalse(history.canRedo)
        assertNull(history.redo(s3Alt))
        assertEquals(listOf(s1, s2, s3), history.toList())
    }

    @Test fun undoRedoHistoryLoadHistoryTrimsToCapacityAndClearResets() {
        val history = UndoRedoHistory<GameState>(maxSize = 2)
        val items = listOf(sampleState(1, 1), sampleState(2, 2), sampleState(3, 3))
        history.loadHistory(items)

        assertEquals(2, history.toList().size)
        assertEquals(items[1], history.firstOrNull())
        assertFalse(history.canRedo)

        history.clear()
        assertTrue(history.isEmpty())
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.firstOrNull())
    }

    @Test fun scoreEntryLeaderboardComparatorSortsByScoreThenTimeThenMoves() {
        val entries = listOf(
            ScoreEntry(score = 500, moves = 80, timeSec = 0L, dateMillis = 1L), // zero time treated last
            ScoreEntry(score = 500, moves = 90, timeSec = 60L, dateMillis = 2L),
            ScoreEntry(score = 500, moves = 70, timeSec = 60L, dateMillis = 3L), // same score & time, fewer moves
            ScoreEntry(score = 500, moves = 95, timeSec = 45L, dateMillis = 4L), // faster time beats moves
            ScoreEntry(score = 700, moves = 120, timeSec = 100L, dateMillis = 5L), // highest score
            ScoreEntry(score = 500, moves = 0, timeSec = 60L, dateMillis = 6L), // zero moves treated last
        )
        val sorted = entries.sortedWith(ScoreEntry.LeaderboardComparator)

        assertEquals(5L, sorted[0].dateMillis)
        assertEquals(4L, sorted[1].dateMillis)
        assertEquals(3L, sorted[2].dateMillis)
        assertEquals(2L, sorted[3].dateMillis)
        assertEquals(6L, sorted[4].dateMillis)
        assertEquals(1L, sorted[5].dateMillis)
    }

    @Test fun statsTracksWinsLossesStreaksBestTimeWinPercentageAndTop10Leaderboard() {
        var stats = Stats()
        assertEquals(0, stats.winPercentage)

        // Record 11 wins; verify topScores caps at 10, rank is accurate, and streaks increment.
        for (i in 1..11) {
            val entry = ScoreEntry(
                score = i * 100,
                moves = 120 - i,
                timeSec = (200 - i * 10).toLong(),
                dateMillis = i.toLong(),
            )
            val (next, rank) = stats.recordWin(entry)
            stats = next
            assertEquals(1, rank) // each successive score is higher than all previous ones
            assertEquals(i, stats.streak)
            assertEquals(i, stats.bestStreak)
        }

        assertEquals(11, stats.played)
        assertEquals(11, stats.won)
        assertEquals(100, stats.winPercentage)
        assertEquals(1100, stats.bestScore)
        assertEquals(90L, stats.bestTimeSec)
        assertEquals(10, stats.topScores.size)
        assertEquals(1100, stats.topScores.first().score)
        assertEquals(200, stats.topScores.last().score)

        // A lower score that doesn't make top 10 returns rank == null, but updates win stats and streak.
        val lowWin = ScoreEntry(score = 50, moves = 150, timeSec = 300L, dateMillis = 12L)
        val (afterLowWin, lowRank) = stats.recordWin(lowWin)
        stats = afterLowWin
        assertNull(lowRank)
        assertEquals(10, stats.topScores.size)
        assertEquals(12, stats.won)
        assertEquals(12, stats.streak)
        assertEquals(12, stats.bestStreak)
        assertEquals(90L, stats.bestTimeSec)

        // Loss increments played, resets current streak, preserves bestStreak, and updates winPercentage.
        stats = stats.recordLoss()
        assertEquals(13, stats.played)
        assertEquals(12, stats.won)
        assertEquals(0, stats.streak)
        assertEquals(12, stats.bestStreak)
        assertEquals(12 * 100 / 13, stats.winPercentage)

        // New win restarts streak at 1 without shrinking bestStreak.
        val (afterRecoveryWin, _) = stats.recordWin(ScoreEntry(score = 1500, moves = 70, timeSec = 50L, dateMillis = 13L))
        assertEquals(1, afterRecoveryWin.streak)
        assertEquals(12, afterRecoveryWin.bestStreak)
        assertEquals(50L, afterRecoveryWin.bestTimeSec)
        assertEquals(1500, afterRecoveryWin.bestScore)
    }

    @Test fun configAndSettingsDefaultsAreConsistent() {
        val settings = Settings()
        assertEquals(DefaultGameConfig.rules.defaultDrawCount, settings.drawCount)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertTrue(settings.solverEnabled)
        assertEquals(52, DefaultGameConfig.rules.deckSize)
        assertEquals(5_500L, DefaultGameConfig.solver.interactiveMaxDurationMs)
    }

    @Test fun gameStateSerializerRoundTripsAndCachesEncodedState() {
        val initial = Engine.dealRawGame(3, kotlin.random.Random(42L))
        val afterDraw = Engine.draw(initial)!!
        val enriched = afterDraw.copy(
            foundations = listOf(
                listOf(Card(Suit.SPADES, Card.RANK_ACE, faceUp = true)),
                emptyList(),
                emptyList(),
                emptyList(),
            ),
            score = 135,
            moves = 17,
        )

        val encodedFirst = GameStateSerializer.encodeState(enriched)
        val encodedSecond = GameStateSerializer.encodeState(enriched)
        assertSame(encodedFirst, encodedSecond)
        assertEquals(enriched, GameStateSerializer.decodeState(encodedFirst))
    }

    @Test fun gameStateSerializerRoundTripsSavedGameWithBoundedHistoryAndHandlesMalformedInput() {
        val s0 = Engine.dealRawGame(1, kotlin.random.Random(7L))
        val s1 = Engine.draw(s0)!!
        val s2 = Engine.draw(s1)!!
        val s3 = s2.withPile(
            Pile.Foundation(0),
            listOf(Card(Suit.SPADES, Card.RANK_ACE, faceUp = true)),
        ).copy(score = 45, moves = 5)

        val payload = GameStateSerializer.encodeSavedGame(
            state = s3,
            elapsed = 94L,
            history = listOf(s0, s1, s2),
            initial = s0,
            historyLimit = 2,
        ).toString()

        val decoded = GameStateSerializer.decodeSavedGame(payload)!!
        assertEquals(s3, decoded.first)
        assertEquals(94L, decoded.second)
        assertEquals(listOf(s1, s2), decoded.third)
        assertEquals(s0, GameStateSerializer.decodeInitialDeal(payload))

        val withoutInitial = GameStateSerializer.encodeSavedGame(
            state = s2,
            elapsed = 12L,
            history = emptyList(),
            initial = null,
        ).toString()
        assertNull(GameStateSerializer.decodeInitialDeal(withoutInitial))

        assertNull(GameStateSerializer.decodeSavedGame("{not valid json"))
        assertNull(GameStateSerializer.decodeSavedGame("{\"stock\":[]}"))
        assertNull(GameStateSerializer.decodeInitialDeal("{not valid json"))
    }

    @Test fun gameStateSerializerRoundTripsTopScoresAndRejectsMalformedJson() {
        val entries = listOf(
            ScoreEntry(score = 920, moves = 78, timeSec = 115L, dateMillis = 1_768_478_400_000L, drawCount = 1),
            ScoreEntry(score = 740, moves = 104, timeSec = 185L, dateMillis = 1_768_564_800_000L, drawCount = 3),
        )
        val json = GameStateSerializer.encodeTopScores(entries).toString()
        assertEquals(entries, GameStateSerializer.decodeTopScores(json))
        assertNull(GameStateSerializer.decodeTopScores(null))
        assertNull(GameStateSerializer.decodeTopScores("{not an array"))
    }

    @Test fun hudTimeAndScoreDateFormattingAreDeterministic() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("1:05", formatTime(65L))
        assertEquals("60:01", formatTime(3601L))

        assertEquals("Previous Best", formatScoreDate(0L))
        assertEquals("Previous Best", formatScoreDate(-500L))

        val utcZone = ZoneId.of("UTC")
        val millis = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        assertEquals("Jan 15, 2026", formatScoreDate(millis, zoneId = utcZone))
    }
}
