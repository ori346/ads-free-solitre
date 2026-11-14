package com.adfree.solitaire

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.DefaultUiConfig
import com.adfree.solitaire.data.ScoreEntry
import com.adfree.solitaire.data.Settings
import com.adfree.solitaire.data.Stats
import com.adfree.solitaire.data.Storage
import com.adfree.solitaire.game.Engine
import com.adfree.solitaire.game.GameState
import com.adfree.solitaire.game.Hint
import com.adfree.solitaire.game.Pile
import com.adfree.solitaire.game.SolverMove
import com.adfree.solitaire.game.TapCycleTracker
import com.adfree.solitaire.game.UndoRedoHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the interactive board solvability check dialog. */
sealed interface SolverDialogState {
    /** Solver is actively searching for a winning sequence on a background thread. */
    data object Checking : SolverDialogState

    /** Board is solvable with [winningMoves] leading to victory. */
    data class Solvable(
        val winningMoves: List<SolverMove>,
        val exploredStates: Int,
    ) : SolverDialogState

    /** No winning sequence was found within the search budget. */
    data class Unsolvable(val exploredStates: Int) : SolverDialogState
}

/**
 * ViewModel orchestrating active gameplay state, undo/redo history, tap cycling, hints,
 * auto-complete animation, persistence, and player statistics.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Storage(app)
    private val undoHistory = UndoRedoHistory<GameState>(DefaultGameConfig.rules.maxUndoHistory)
    private val tapCycleTracker = TapCycleTracker()

    var settings by mutableStateOf(store.loadSettings().also { Engine.debugMode = it.debugMode })
        private set
    var stats by mutableStateOf(store.loadStats())
        private set
    var debugBanner by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf(dealNewGame())
        private set
    var elapsed by mutableLongStateOf(0L)
        private set
    var hint by mutableStateOf<Hint?>(null)
        private set
    var hintTrigger by mutableIntStateOf(0)
        private set
    var noMoves by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var autoCompleting by mutableStateOf(false)
        private set
    var solverDialogState by mutableStateOf<SolverDialogState?>(null)
        private set
    var solverPlaying by mutableStateOf(false)
        private set
    var solverCurrentStep by mutableIntStateOf(0)
        private set
    var solverTotalSteps by mutableIntStateOf(0)
        private set
    var lastSolverMove by mutableStateOf<SolverMove?>(null)
        private set
    var solvedBySolver by mutableStateOf(false)
        private set
    var showWin by mutableStateOf(false)
        private set
    var lastWinRank by mutableStateOf<Int?>(null)
        private set

    private var initialDeal: GameState? = null
    private var autoCompletable by mutableStateOf(false)
    private var hintCycleIndex = 0
    private var active = false
    private var dealForfeitedToSolver = false
    private var autoJob: Job? = null
    private var solverSearchJob: Job? = null
    private var solverPlaybackJob: Job? = null

    private val isBusyWithAutomation: Boolean get() = autoCompleting || solverPlaying

    /** True when the current deal has progress that can be reset back to its initial layout. */
    val canRestartDeal: Boolean get() = !undoHistory.isEmpty() || (initialDeal != null && state.moves > 0)

    init {
        store.loadGame()?.let { (g, t, h) ->
            if (!g.isWon) {
                state = g
                elapsed = t
                undoHistory.loadHistory(h)
                canUndo = undoHistory.canUndo
                initialDeal = store.loadInitialDeal() ?: undoHistory.firstOrNull()
            }
        }
        if (initialDeal == null && state.moves == 0) initialDeal = state
        refreshAutoCompletable()
        maybeAutoComplete()
        viewModelScope.launch {
            while (true) {
                delay(DefaultGameConfig.rules.timerTickMs)
                if (active && state.moves > 0 && !state.isWon) elapsed++
            }
        }
    }

    private fun dealNewGame(): GameState {
        var rejectedCount = 0
        val dealt = Engine.newGame(
            drawCount = settings.drawCount,
            debug = settings.debugMode,
            onUnsolvableDeal = { attempt, _ ->
                rejectedCount++
                if (settings.debugMode) {
                    Log.d(
                        SOLVER_LOG_TAG,
                        "Unsolvable deal drawn on attempt #$attempt (drawCount=${settings.drawCount}) — redrawing...",
                    )
                }
            },
        )
        debugBanner = if (settings.debugMode && rejectedCount > 0) {
            "Debug: Skipped $rejectedCount unsolvable deal${if (rejectedCount > 1) "s" else ""} before dealing this solvable board"
        } else {
            null
        }
        return dealt
    }

    /** Dismisses any active debug deal-rejection banner. */
    fun clearDebugBanner() {
        debugBanner = null
    }

    /** Updates foreground activity state and persists the current game when backgrounded. */
    fun setActive(a: Boolean) {
        active = a
        if (!a) save()
    }

    private fun save() = store.saveGame(state, elapsed, undoHistory.toList(), initialDeal)

    private fun apply(next: GameState?, clearTapCycle: Boolean = true): Boolean {
        if (next == null || isBusyWithAutomation) return false
        commit(next, clearTapCycle)
        return true
    }

    private fun commit(next: GameState, clearTapCycle: Boolean = true) {
        if (clearTapCycle) tapCycleTracker.clear()
        undoHistory.record(state)
        canUndo = undoHistory.canUndo
        canRedo = undoHistory.canRedo
        state = next
        clearHintState()
        if (!isBusyWithAutomation) refreshAutoCompletable()
        if (next.isWon) onWin()
        if (!isBusyWithAutomation) save()
        if (!solverPlaying) maybeAutoComplete()
    }

    private fun clearHintState() {
        hint = null
        hintCycleIndex = 0
        noMoves = false
    }

    private fun maybeAutoComplete() {
        if (settings.autoComplete && canAutoComplete) autoComplete()
    }

    private fun onWin() {
        if (dealForfeitedToSolver) {
            solvedBySolver = true
            lastWinRank = null
            undoHistory.clear()
            canUndo = false
            canRedo = false
            showWin = true
            return
        }
        val entry = ScoreEntry(
            score = state.score,
            moves = state.moves,
            timeSec = elapsed,
            dateMillis = System.currentTimeMillis(),
            drawCount = state.drawCount,
        )
        val (updatedStats, rank) = stats.recordWin(entry, DefaultGameConfig.rules.leaderboardSize)
        stats = updatedStats
        lastWinRank = rank
        store.saveStats(stats)
        undoHistory.clear()
        canUndo = false
        canRedo = false
        solvedBySolver = false
        showWin = true
    }

    /** Hides the victory dialog while keeping the won board visible. */
    fun dismissWin() {
        showWin = false
    }

    /** Starts a fresh solvable deal using the configured draw count. */
    fun newGame() = resetToDeal(dealNewGame())

    /** Restarts the current deal from its initial card layout. */
    fun restartDeal() {
        if (undoHistory.isEmpty() && (initialDeal == null || state.moves == 0)) return
        val first = initialDeal ?: undoHistory.firstOrNull() ?: state
        resetToDeal(first)
    }

    private fun resetToDeal(nextDeal: GameState) {
        autoJob?.cancel()
        solverSearchJob?.cancel()
        solverPlaybackJob?.cancel()
        autoCompleting = false
        solverPlaying = false
        solverDialogState = null
        lastSolverMove = null
        solverCurrentStep = 0
        solverTotalSteps = 0
        tapCycleTracker.clear()
        if (state.moves > 0 && !state.isWon && !dealForfeitedToSolver) {
            stats = stats.recordLoss()
            store.saveStats(stats)
        }
        dealForfeitedToSolver = false
        solvedBySolver = false
        undoHistory.clear()
        canUndo = false
        canRedo = false
        state = nextDeal
        initialDeal = nextDeal
        refreshAutoCompletable()
        elapsed = 0L
        clearHintState()
        showWin = false
        lastWinRank = null
        save()
    }

    /** Reverts the most recent player move when available. */
    fun undo() {
        if (isBusyWithAutomation || state.isWon) return
        val prev = undoHistory.undo(state) ?: return
        applyHistoryNavigation(prev)
    }

    /** Reapplies the most recently undone move when available. */
    fun redo() {
        if (isBusyWithAutomation || state.isWon) return
        val next = undoHistory.redo(state) ?: return
        applyHistoryNavigation(next)
    }

    private fun applyHistoryNavigation(targetState: GameState) {
        tapCycleTracker.clear()
        state = targetState
        refreshAutoCompletable()
        canUndo = undoHistory.canUndo
        canRedo = undoHistory.canRedo
        clearHintState()
        save()
    }

    /** Draws from stock or recycles waste into stock. Returns true when state changed. */
    fun draw(): Boolean = apply(Engine.draw(state))

    /** Attempts a direct move from [from] at [index] onto [to]. Returns true if legal and applied. */
    fun move(from: Pile, index: Int, to: Pile): Boolean = apply(Engine.move(state, from, index, to))

    /** Resolves a tap on [pile] at [index], cycling away from already-visited destinations. */
    fun resolveTapMove(pile: Pile, index: Int): Pair<Int, Pile>? =
        tapCycleTracker.resolveTapMove(state, pile, index)

    /** Executes a resolved tap move and updates tap-cycle state so repeated taps cycle alternatives. */
    fun tapMove(from: Pile, resolvedIndex: Int, to: Pile): Boolean {
        val prevState = state
        val next = Engine.move(prevState, from, resolvedIndex, to)
        if (!apply(next, clearTapCycle = false)) return false
        if (state.isWon) {
            tapCycleTracker.clear()
        } else {
            tapCycleTracker.recordMove(prevState, from, resolvedIndex, to)
        }
        return true
    }

    /** Tap a card: sends it (or its parent stack) to the smartest legal destination. */
    fun tap(pile: Pile, index: Int): Boolean {
        if (pile == Pile.Stock) return draw()
        val (resolvedIndex, target) = resolveTapMove(pile, index) ?: return false
        return tapMove(pile, resolvedIndex, target)
    }

    /** Cycles through prioritized legal hints, or flags `noMoves` if none exist. */
    fun showHint() {
        val all = Engine.findHints(state)
        if (all.isEmpty()) {
            hint = null
            noMoves = true
            hintTrigger++
            return
        }
        val current = hint
        val idx = if (current != null) {
            val prevIdx = all.indexOf(current)
            if (prevIdx >= 0) (prevIdx + 1) % all.size else hintCycleIndex % all.size
        } else {
            hintCycleIndex % all.size
        }
        hint = all[idx]
        hintCycleIndex = (idx + 1) % all.size
        noMoves = false
        hintTrigger++
    }

    /** Clears any currently displayed hint or no-moves indicator. */
    fun clearHint() {
        hint = null
        noMoves = false
    }

    /** True when the current board can be safely auto-completed to victory. */
    val canAutoComplete: Boolean get() = !isBusyWithAutomation && autoCompletable

    private fun refreshAutoCompletable() {
        autoCompletable = Engine.canAutoComplete(state)
    }

    /**
     * Runs the omniscient solver on a background coroutine (up to `interactiveMaxDurationMs`)
     * to check whether the current board configuration is still winnable.
     */
    fun runSolverCheck() {
        if (isBusyWithAutomation || state.isWon || solverDialogState is SolverDialogState.Checking) return
        clearHintState()
        val snapshot = state
        val solverConfig = DefaultGameConfig.solver
        solverDialogState = SolverDialogState.Checking
        solverSearchJob?.cancel()
        solverSearchJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val result = withContext(Dispatchers.Default) {
                Engine.solveOmniscient(
                    initial = snapshot,
                    maxStates = solverConfig.interactiveMaxStates,
                    maxDurationMs = solverConfig.interactiveMaxDurationMs,
                )
            }
            val elapsedMs = System.currentTimeMillis() - startMs
            val remainingMinMs = solverConfig.interactiveMinDisplayMs - elapsedMs
            if (remainingMinMs > 0L) delay(remainingMinMs)
            solverDialogState = if (result.isSolvable) {
                SolverDialogState.Solvable(
                    winningMoves = result.winningMoves,
                    exploredStates = result.exploredStates,
                )
            } else {
                SolverDialogState.Unsolvable(exploredStates = result.exploredStates)
            }
        }
    }

    /** Cancels any active solver search and closes the solver dialog. */
    fun dismissSolverDialog() {
        solverSearchJob?.cancel()
        solverSearchJob = null
        solverDialogState = null
    }

    /**
     * Records the current deal as a loss and automatically plays out the solver's winning
     * move sequence step by step on the board.
     */
    fun watchSolverSolution() {
        val solvable = solverDialogState as? SolverDialogState.Solvable ?: return
        val moves = solvable.winningMoves
        solverDialogState = null
        if (moves.isEmpty() || isBusyWithAutomation || state.isWon) return

        if (!dealForfeitedToSolver) {
            dealForfeitedToSolver = true
            stats = stats.recordLoss()
            store.saveStats(stats)
        }

        solverPlaying = true
        solverCurrentStep = 0
        solverTotalSteps = moves.size
        lastSolverMove = null
        solverPlaybackJob?.cancel()
        solverPlaybackJob = viewModelScope.launch {
            try {
                for ((idx, step) in moves.withIndex()) {
                    val next = when (step) {
                        SolverMove.Draw -> Engine.draw(state)
                        is SolverMove.Move -> Engine.move(state, step.from, step.index, step.to)
                    } ?: break
                    solverCurrentStep = idx + 1
                    lastSolverMove = step
                    commit(next)
                    if (next.isWon) break
                    val stepDelayMs = if (next.areAllTableauCardsFaceUp) {
                        DefaultUiConfig.animation.autoCompleteStepDelayMs
                    } else {
                        DefaultUiConfig.animation.solverStepDelayMs
                    }
                    delay(stepDelayMs)
                }
            } finally {
                solverPlaying = false
                lastSolverMove = null
                refreshAutoCompletable()
                save()
            }
        }
    }

    /** Stops an in-progress step-by-step solver playback. */
    fun stopSolverPlayback() {
        solverPlaybackJob?.cancel()
        solverPlaybackJob = null
        solverPlaying = false
        lastSolverMove = null
        refreshAutoCompletable()
        save()
    }

    /** Animated step-by-step auto-completion of a guaranteed winning board. */
    fun autoComplete() {
        if (!canAutoComplete) return
        autoCompleting = true
        autoJob = viewModelScope.launch {
            var stuck = false
            var found = state.foundationCardCount
            var stalled = 0
            try {
                while (!state.isWon) {
                    val next = Engine.autoCompleteStep(state)
                    if (next == null) {
                        stuck = true
                        break
                    }
                    commit(next)
                    if (next.isWon) break
                    val f = next.foundationCardCount
                    if (f > found) {
                        found = f
                        stalled = 0
                    } else if (++stalled > Engine.autoCompleteStallLimit(next)) {
                        stuck = true
                        break
                    }
                    delay(DefaultUiConfig.animation.autoCompleteStepDelayMs)
                }
            } finally {
                autoCompleting = false
                refreshAutoCompletable()
                save()
                if (!stuck && !state.isWon) maybeAutoComplete()
            }
        }
    }

    /** Persists updated user [Settings] and applies debug/auto-complete changes immediately. */
    fun updateSettings(s: Settings) {
        settings = s
        Engine.debugMode = s.debugMode
        if (!s.debugMode) debugBanner = null
        if (!s.solverEnabled) {
            dismissSolverDialog()
            if (solverPlaying) stopSolverPlayback()
        }
        store.saveSettings(s)
        if (!solverPlaying) maybeAutoComplete()
    }

    /** Clears all lifetime player statistics and leaderboard entries. */
    fun resetStats() {
        stats = Stats()
        store.saveStats(stats)
    }

    private companion object {
        const val SOLVER_LOG_TAG = "SolitaireSolver"
    }
}
