package com.adfree.solitaire.data

import android.content.Context
import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.game.GameState

/** All data stays on-device in private SharedPreferences. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Loads persisted user [Settings], falling back to defaults for missing keys. */
    fun loadSettings(): Settings {
        val defaults = Settings()
        return Settings(
            drawCount = prefs.getInt(KEY_DRAW_COUNT, defaults.drawCount),
            theme = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, defaults.theme.ordinal)) {
                defaults.theme
            },
            cardBack = prefs.getInt(KEY_CARD_BACK, defaults.cardBack),
            autoComplete = prefs.getBoolean(KEY_AUTO_COMPLETE, defaults.autoComplete),
            leftHanded = prefs.getBoolean(KEY_LEFT_HANDED, defaults.leftHanded),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            fourColor = prefs.getBoolean(KEY_FOUR_COLOR, defaults.fourColor),
            solverEnabled = prefs.getBoolean(KEY_SOLVER_ENABLED, defaults.solverEnabled),
            debugMode = prefs.getBoolean(KEY_DEBUG_MODE, defaults.debugMode),
        )
    }

    /** Persists user [Settings] asynchronously to private SharedPreferences. */
    fun saveSettings(s: Settings) = prefs.edit()
        .putInt(KEY_DRAW_COUNT, s.drawCount)
        .putInt(KEY_THEME, s.theme.ordinal)
        .putInt(KEY_CARD_BACK, s.cardBack)
        .putBoolean(KEY_AUTO_COMPLETE, s.autoComplete)
        .putBoolean(KEY_LEFT_HANDED, s.leftHanded)
        .putBoolean(KEY_HAPTICS, s.haptics)
        .putBoolean(KEY_FOUR_COLOR, s.fourColor)
        .putBoolean(KEY_SOLVER_ENABLED, s.solverEnabled)
        .putBoolean(KEY_DEBUG_MODE, s.debugMode)
        .apply()

    /** Loads persisted player [Stats] and leaderboard entries. */
    fun loadStats(): Stats {
        val defaults = Stats()
        val played = prefs.getInt(KEY_PLAYED, defaults.played)
        val won = prefs.getInt(KEY_WON, defaults.won)
        val bestScore = prefs.getInt(KEY_BEST_SCORE, defaults.bestScore)
        val bestTimeSec = prefs.getLong(KEY_BEST_TIME, defaults.bestTimeSec)
        val streak = prefs.getInt(KEY_STREAK, defaults.streak)
        val bestStreak = prefs.getInt(KEY_BEST_STREAK, defaults.bestStreak)
        val parsedScores = GameStateSerializer.decodeTopScores(prefs.getString(KEY_TOP_SCORES, null))
        val topScores = when {
            parsedScores != null -> parsedScores
            bestScore > 0 -> listOf(
                ScoreEntry(
                    score = bestScore,
                    moves = 0,
                    timeSec = bestTimeSec,
                    dateMillis = 0L,
                    drawCount = prefs.getInt(KEY_DRAW_COUNT, DefaultGameConfig.rules.defaultDrawCount),
                ),
            )
            else -> emptyList()
        }
        return Stats(
            played = played,
            won = won,
            bestScore = bestScore,
            bestTimeSec = bestTimeSec,
            streak = streak,
            bestStreak = bestStreak,
            topScores = topScores,
        )
    }

    /** Persists player [Stats] and leaderboard entries asynchronously. */
    fun saveStats(s: Stats) {
        val scoresJson = GameStateSerializer.encodeTopScores(s.topScores)
        prefs.edit()
            .putInt(KEY_PLAYED, s.played)
            .putInt(KEY_WON, s.won)
            .putInt(KEY_BEST_SCORE, s.bestScore)
            .putLong(KEY_BEST_TIME, s.bestTimeSec)
            .putInt(KEY_STREAK, s.streak)
            .putInt(KEY_BEST_STREAK, s.bestStreak)
            .putString(KEY_TOP_SCORES, scoresJson.toString())
            .apply()
    }

    /** Persists the current game [s], [elapsed] seconds, bounded [history], and optional [initial] deal. */
    fun saveGame(
        s: GameState,
        elapsed: Long,
        history: List<GameState> = emptyList(),
        initial: GameState? = null,
    ) {
        val json = GameStateSerializer.encodeSavedGame(
            state = s,
            elapsed = elapsed,
            history = history,
            initial = initial,
            historyLimit = DefaultGameConfig.rules.persistedHistoryLimit,
        )
        prefs.edit().putString(KEY_GAME, json.toString()).apply()
    }

    /** Loads the saved game state, elapsed seconds, and undo history, or `null` if unavailable. */
    fun loadGame(): Triple<GameState, Long, List<GameState>>? =
        prefs.getString(KEY_GAME, null)?.let { GameStateSerializer.decodeSavedGame(it) }

    /** The original deal of the saved game, or `null` if absent (older saves) or unreadable. */
    fun loadInitialDeal(): GameState? =
        prefs.getString(KEY_GAME, null)?.let { GameStateSerializer.decodeInitialDeal(it) }

    private companion object {
        const val PREFS_NAME = "solitaire"

        const val KEY_DRAW_COUNT = "drawCount"
        const val KEY_THEME = "theme"
        const val KEY_CARD_BACK = "cardBack"
        const val KEY_AUTO_COMPLETE = "autoComplete"
        const val KEY_LEFT_HANDED = "leftHanded"
        const val KEY_HAPTICS = "haptics"
        const val KEY_FOUR_COLOR = "fourColor"
        const val KEY_SOLVER_ENABLED = "solverEnabled"
        const val KEY_DEBUG_MODE = "debugMode"

        const val KEY_PLAYED = "played"
        const val KEY_WON = "won"
        const val KEY_BEST_SCORE = "bestScore"
        const val KEY_BEST_TIME = "bestTime"
        const val KEY_STREAK = "streak"
        const val KEY_BEST_STREAK = "bestStreak"
        const val KEY_TOP_SCORES = "topScores"

        const val KEY_GAME = "game"
    }
}
