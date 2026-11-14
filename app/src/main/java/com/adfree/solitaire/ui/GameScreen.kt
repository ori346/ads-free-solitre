package com.adfree.solitaire.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.adfree.solitaire.GameViewModel
import com.adfree.solitaire.config.DefaultGameConfig
import com.adfree.solitaire.config.DefaultUiConfig
import com.adfree.solitaire.config.UiConfig
import com.adfree.solitaire.ui.board.DragOverlay
import com.adfree.solitaire.ui.board.FlightOverlay
import com.adfree.solitaire.ui.board.TableauGrid
import com.adfree.solitaire.ui.board.TopPlayRow
import com.adfree.solitaire.ui.board.rememberBoardGestureState
import com.adfree.solitaire.ui.dialogs.ConfirmDealDialog
import com.adfree.solitaire.ui.dialogs.HelpDialog
import com.adfree.solitaire.ui.dialogs.SettingsDialog
import com.adfree.solitaire.ui.dialogs.SolverDialog
import com.adfree.solitaire.ui.dialogs.StatsDialog
import com.adfree.solitaire.ui.dialogs.WinDialog
import com.adfree.solitaire.ui.hud.BottomControlPill
import com.adfree.solitaire.ui.hud.StatusBannerOverlay
import com.adfree.solitaire.ui.hud.TopHudBar
import com.adfree.solitaire.ui.hud.WinConfettiOverlay
import com.adfree.solitaire.ui.theme.SolitaireColors
import kotlinx.coroutines.delay

private const val TAB_TOP_SCORES = 0

/**
 * Top-level Solitaire screen orchestrating the HUD, playable card board, gesture/flight overlays,
 * celebratory effects, and modal dialogs.
 */
@Composable
fun GameScreen(
    vm: GameViewModel,
    dark: Boolean,
    config: UiConfig = DefaultUiConfig,
) = CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    val s = vm.state
    val settings = vm.settings
    val hint = vm.hint
    val gestureState = rememberBoardGestureState(
        vm = vm,
        config = config,
        hapticsEnabled = settings.haptics,
    )

    var prevObservedState by remember { mutableStateOf(s) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var statsInitialTab by remember { mutableIntStateOf(TAB_TOP_SCORES) }
    var confirmNew by remember { mutableStateOf(false) }

    LaunchedEffect(s) {
        val prev = prevObservedState
        prevObservedState = s
        gestureState.handleAutoCompleteFlight(prev, s)
    }

    LaunchedEffect(hint, vm.noMoves, vm.hintTrigger, config.animation.hintDisplayDurationMs) {
        if (hint != null || vm.noMoves) {
            delay(config.animation.hintDisplayDurationMs)
            vm.clearHint()
        }
    }

    LaunchedEffect(vm.debugBanner, config.animation.debugBannerDurationMs) {
        if (vm.debugBanner != null) {
            delay(config.animation.debugBannerDurationMs)
            vm.clearDebugBanner()
        }
    }

    val tableColor = if (dark) SolitaireColors.TableDark else SolitaireColors.TableLight

    Scaffold(
        containerColor = tableColor,
        topBar = {
            TopHudBar(
                score = s.score,
                moves = s.moves,
                elapsed = vm.elapsed,
                dark = dark,
                onShowScores = {
                    statsInitialTab = TAB_TOP_SCORES
                    showStats = true
                },
                onShowHelp = { showHelp = true },
                onShowSettings = { showSettings = true },
            )
        },
        bottomBar = {
            BottomControlPill(
                vm = vm,
                dark = dark,
                animConfig = config.animation,
                onNewGame = { if (s.moves > 0 && !s.isWon) confirmNew = true else vm.newGame() },
            )
        },
    ) { pad ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .onGloballyPositioned { gestureState.boardPos = it.positionInWindow() },
        ) {
            val layout = config.layout
            val colCount = DefaultGameConfig.rules.tableauColumnCount
            val widthBoundCardW = (maxWidth - layout.boardGap * (colCount + 1)) / colCount
            val heightBoundCardW = maxHeight / layout.maxBoardHeightWidthRatio
            val cardW = minOf(widthBoundCardW, heightBoundCardW)
            val cardH = cardW * layout.cardAspectRatio
            val density = LocalDensity.current
            val cardWpx = with(density) { cardW.toPx() }
            val cardHpx = with(density) { cardH.toPx() }

            SideEffect {
                gestureState.cardWpx = cardWpx
                gestureState.cardHpx = cardHpx
            }

            val activeDragTarget = gestureState.drag?.let { gestureState.findActiveDropTarget(it) }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = layout.boardGap, vertical = layout.boardVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopPlayRow(
                    state = s,
                    gestureState = gestureState,
                    hint = hint,
                    activeDragTarget = activeDragTarget,
                    cardW = cardW,
                    cardH = cardH,
                    cardWpx = cardWpx,
                    back = settings.cardBack,
                    dark = dark,
                    leftHanded = settings.leftHanded,
                    fourColor = settings.fourColor,
                    layout = layout,
                )

                TableauGrid(
                    state = s,
                    gestureState = gestureState,
                    hint = hint,
                    activeDragTarget = activeDragTarget,
                    cardW = cardW,
                    cardH = cardH,
                    cardHpx = cardHpx,
                    back = settings.cardBack,
                    dark = dark,
                    fourColor = settings.fourColor,
                    layout = layout,
                )
            }

            FlightOverlay(
                gestureState = gestureState,
                cardW = cardW,
                cardH = cardH,
                back = settings.cardBack,
                dark = dark,
                fourColor = settings.fourColor,
                layout = layout,
            )

            DragOverlay(
                drag = gestureState.drag,
                activeDragTarget = activeDragTarget,
                cardW = cardW,
                cardH = cardH,
                back = settings.cardBack,
                dark = dark,
                fourColor = settings.fourColor,
                layout = layout,
            )

            StatusBannerOverlay(
                noMoves = vm.noMoves,
                debugBanner = vm.debugBanner,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            if (vm.showWin && !vm.solvedBySolver) {
                WinConfettiOverlay(animConfig = config.animation)
            }
        }
    }

    vm.solverDialogState?.let { solverState ->
        SolverDialog(
            dialogState = solverState,
            canRestartDeal = vm.canRestartDeal,
            activeStreak = if (!s.isWon) vm.stats.streak else 0,
            onWatchSolution = { vm.watchSolverSolution() },
            onRestartDeal = { vm.restartDeal() },
            onNewDeal = { vm.newGame() },
            onDismiss = { vm.dismissSolverDialog() },
        )
    }

    if (confirmNew) {
        ConfirmDealDialog(
            canUndo = vm.canUndo,
            activeStreak = if (s.moves > 0 && !s.isWon) vm.stats.streak else 0,
            onNewDeal = {
                confirmNew = false
                vm.newGame()
            },
            onRestartDeal = {
                confirmNew = false
                vm.restartDeal()
            },
            onDismiss = { confirmNew = false },
        )
    }

    if (vm.showWin) {
        WinDialog(
            score = s.score,
            moves = s.moves,
            elapsed = vm.elapsed,
            streak = vm.stats.streak,
            rank = vm.lastWinRank,
            solvedBySolver = vm.solvedBySolver,
            onPlayAgain = { vm.newGame() },
            onTopScores = {
                vm.dismissWin()
                statsInitialTab = TAB_TOP_SCORES
                showStats = true
            },
            onDismiss = { vm.dismissWin() },
        )
    }

    if (showStats) {
        StatsDialog(
            stats = vm.stats,
            initialTab = statsInitialTab,
            onResetStats = { vm.resetStats() },
            onDismiss = { showStats = false },
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            currentDrawCount = s.drawCount,
            dark = dark,
            onUpdateSettings = { vm.updateSettings(it) },
            onDismiss = { showSettings = false },
        )
    }
}
