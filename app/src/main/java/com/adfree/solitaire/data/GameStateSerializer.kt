package com.adfree.solitaire.data

import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.game.Card
import com.adfree.solitaire.game.GameState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap

/**
 * Pure JSON serializer/deserializer for [GameState], [ScoreEntry] leaderboards, and saved-game payloads.
 */
object GameStateSerializer {
    /** Offset added to a card's numeric ID when serialized face-up. */
    const val FACE_UP_CARD_OFFSET = 100

    /** Current saved-game JSON schema version. */
    const val SCHEMA_VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_ELAPSED = "elapsed"
    private const val KEY_INITIAL = "initial"
    private const val KEY_HISTORY = "history"

    private const val KEY_STOCK = "stock"
    private const val KEY_WASTE = "waste"
    private const val KEY_FOUNDATIONS = "foundations"
    private const val KEY_TABLEAU = "tableau"
    private const val KEY_DRAW_COUNT = "drawCount"
    private const val KEY_SCORE = "score"
    private const val KEY_MOVES = "moves"

    private const val KEY_TIME_SEC = "timeSec"
    private const val KEY_DATE_MILLIS = "dateMillis"

    private val stateCache = Collections.synchronizedMap(WeakHashMap<GameState, JSONObject>())

    /** Encodes a [GameState] into a [JSONObject], reusing cached JSON per state instance. */
    fun encodeState(s: GameState): JSONObject {
        stateCache[s]?.let { return it }
        val obj = JSONObject()
            .put(KEY_STOCK, encodeCards(s.stock))
            .put(KEY_WASTE, encodeCards(s.waste))
            .put(KEY_FOUNDATIONS, encodeCardLists(s.foundations))
            .put(KEY_TABLEAU, encodeCardLists(s.tableau))
            .put(KEY_DRAW_COUNT, s.drawCount)
            .put(KEY_SCORE, s.score)
            .put(KEY_MOVES, s.moves)
        stateCache[s] = obj
        return obj
    }

    /** Decodes a [GameState] from its [JSONObject] representation. */
    fun decodeState(j: JSONObject): GameState = GameState(
        stock = decodeCards(j.getJSONArray(KEY_STOCK)),
        waste = decodeCards(j.getJSONArray(KEY_WASTE)),
        foundations = decodeCardLists(j.getJSONArray(KEY_FOUNDATIONS)),
        tableau = decodeCardLists(j.getJSONArray(KEY_TABLEAU)),
        drawCount = j.getInt(KEY_DRAW_COUNT),
        score = j.getInt(KEY_SCORE),
        moves = j.getInt(KEY_MOVES),
    )

    /** Encodes a list of leaderboard [ScoreEntry] items into a [JSONArray]. */
    fun encodeTopScores(scores: List<ScoreEntry>): JSONArray = JSONArray(
        scores.map { entry ->
            JSONObject()
                .put(KEY_SCORE, entry.score)
                .put(KEY_MOVES, entry.moves)
                .put(KEY_TIME_SEC, entry.timeSec)
                .put(KEY_DATE_MILLIS, entry.dateMillis)
                .put(KEY_DRAW_COUNT, entry.drawCount)
        },
    )

    /** Decodes a JSON array string into [ScoreEntry] items, returning `null` if absent or malformed. */
    fun decodeTopScores(jsonStr: String?): List<ScoreEntry>? {
        if (jsonStr == null) return null
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { index ->
                val obj = arr.getJSONObject(index)
                ScoreEntry(
                    score = obj.getInt(KEY_SCORE),
                    moves = obj.optInt(KEY_MOVES, 0),
                    timeSec = obj.optLong(KEY_TIME_SEC, 0L),
                    dateMillis = obj.optLong(KEY_DATE_MILLIS, 0L),
                    drawCount = obj.optInt(KEY_DRAW_COUNT, DefaultGameConfig.rules.defaultDrawCount),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encodes the active [state], [elapsed] seconds, bounded undo [history], and optional [initial]
     * deal into a self-contained saved-game [JSONObject].
     */
    fun encodeSavedGame(
        state: GameState,
        elapsed: Long,
        history: List<GameState> = emptyList(),
        initial: GameState? = null,
        historyLimit: Int = DefaultGameConfig.rules.persistedHistoryLimit,
    ): JSONObject {
        val base = encodeState(state)
        val json = JSONObject()
        val keys = base.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            json.put(key, base.get(key))
        }
        json.put(KEY_VERSION, SCHEMA_VERSION)
        json.put(KEY_ELAPSED, elapsed)
        if (initial != null) {
            json.put(KEY_INITIAL, encodeState(initial))
        }
        val boundedHistory = history.takeLast(historyLimit.coerceAtLeast(0))
        json.put(KEY_HISTORY, JSONArray(boundedHistory.map { encodeState(it) }))
        return json
    }

    /**
     * Decodes a saved-game JSON string into `(state, elapsed, history)`, returning `null` if
     * malformed or missing required fields.
     */
    fun decodeSavedGame(jsonStr: String): Triple<GameState, Long, List<GameState>>? = try {
        val obj = JSONObject(jsonStr)
        val state = decodeState(obj)
        val elapsed = obj.getLong(KEY_ELAPSED)
        val histArr = obj.optJSONArray(KEY_HISTORY)
        val history = if (histArr != null) {
            (0 until histArr.length()).map { decodeState(histArr.getJSONObject(it)) }
        } else {
            emptyList()
        }
        Triple(state, elapsed, history)
    } catch (_: Exception) {
        null
    }

    /** Decodes the original deal stored inside a saved-game payload, or `null` if absent/malformed. */
    fun decodeInitialDeal(jsonStr: String): GameState? = try {
        JSONObject(jsonStr).optJSONObject(KEY_INITIAL)?.let { decodeState(it) }
    } catch (_: Exception) {
        null
    }

    private fun encodeCards(cards: List<Card>): JSONArray =
        JSONArray(cards.map { it.id + if (it.faceUp) FACE_UP_CARD_OFFSET else 0 })

    private fun decodeCards(array: JSONArray): List<Card> =
        (0 until array.length()).map { index ->
            val encoded = array.getInt(index)
            Card.fromId(encoded % FACE_UP_CARD_OFFSET, encoded >= FACE_UP_CARD_OFFSET)
        }

    private fun encodeCardLists(piles: List<List<Card>>): JSONArray =
        JSONArray(piles.map { encodeCards(it) })

    private fun decodeCardLists(array: JSONArray): List<List<Card>> =
        (0 until array.length()).map { decodeCards(array.getJSONArray(it)) }
}
