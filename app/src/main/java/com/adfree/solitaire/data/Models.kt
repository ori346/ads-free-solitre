package com.adfree.solitaire.data

import com.adfree.solitaire.config.DefaultGameConfig

private const val PERCENT_MULTIPLIER = 100

/** Display theme preference for the application UI. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persisted user preferences for gameplay and presentation.
 *
 * @property drawCount Number of cards drawn per stock tap (1 or 3).
 * @property theme Active theme mode preference.
 * @property cardBack Selected card back design index.
 * @property autoComplete Whether auto-complete triggers automatically once a win is guaranteed.
 * @property leftHanded Whether the top row layout is mirrored for left-handed play.
 * @property haptics Whether tactile feedback is enabled for interactions.
 * @property fourColor Whether four-color suit rendering is enabled.
 * @property solverEnabled Whether the interactive board solver button is shown in the control bar.
 * @property debugMode Whether solver deal-rejection diagnostics are enabled.
 */
data class Settings(
    val drawCount: Int = DefaultGameConfig.rules.defaultDrawCount,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val cardBack: Int = 0,
    val autoComplete: Boolean = true,
    val leftHanded: Boolean = false,
    val haptics: Boolean = true,
    val fourColor: Boolean = false,
    val solverEnabled: Boolean = true,
    val debugMode: Boolean = false,
)

/**
 * Single recorded victory entry for the local leaderboard.
 *
 * @property score Final game score.
 * @property moves Total moves taken to win.
 * @property timeSec Elapsed play time in seconds.
 * @property dateMillis Completion timestamp in epoch milliseconds.
 * @property drawCount Draw count mode used during the game.
 */
data class ScoreEntry(
    val score: Int,
    val moves: Int,
    val timeSec: Long,
    val dateMillis: Long,
    val drawCount: Int = DefaultGameConfig.rules.defaultDrawCount,
) {
    companion object {
        /** Ranks entries by highest score, then fastest positive time, then fewest positive moves. */
        val LeaderboardComparator: Comparator<ScoreEntry> = compareByDescending<ScoreEntry> { it.score }
            .thenBy { if (it.timeSec > 0L) it.timeSec else Long.MAX_VALUE }
            .thenBy { if (it.moves > 0) it.moves else Int.MAX_VALUE }
    }
}

/**
 * Lifetime player statistics and top-score leaderboard.
 *
 * @property played Total number of completed or abandoned games.
 * @property won Total number of games won.
 * @property bestScore Highest score achieved.
 * @property bestTimeSec Fastest winning time in seconds, or 0 if no win is recorded.
 * @property streak Current consecutive win streak.
 * @property bestStreak Longest consecutive win streak achieved.
 * @property topScores Ranked list of top winning entries.
 */
data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val bestScore: Int = 0,
    val bestTimeSec: Long = 0L,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val topScores: List<ScoreEntry> = emptyList(),
) {
    /** Whole-number win percentage in the range 0..100. */
    val winPercentage: Int get() = if (played > 0) won * PERCENT_MULTIPLIER / played else 0

    /**
     * Records a won game and returns the updated [Stats] paired with the entry's 1-based
     * leaderboard rank, or `null` if it did not place within [maxTopScores].
     */
    fun recordWin(
        entry: ScoreEntry,
        maxTopScores: Int = DefaultGameConfig.rules.leaderboardSize,
    ): Pair<Stats, Int?> {
        val nextStreak = streak + 1
        val updatedScores = (topScores + entry)
            .sortedWith(ScoreEntry.LeaderboardComparator)
            .take(maxTopScores.coerceAtLeast(0))
        val rankIndex = updatedScores.indexOf(entry)
        val rank = if (rankIndex >= 0) rankIndex + 1 else null
        val updated = copy(
            played = played + 1,
            won = won + 1,
            bestScore = maxOf(bestScore, entry.score),
            bestTimeSec = if (bestTimeSec == 0L) entry.timeSec else minOf(bestTimeSec, entry.timeSec),
            streak = nextStreak,
            bestStreak = maxOf(bestStreak, nextStreak),
            topScores = updatedScores,
        )
        return updated to rank
    }

    /** Records a lost or abandoned game by incrementing [played] and resetting [streak]. */
    fun recordLoss(): Stats = copy(played = played + 1, streak = 0)
}
