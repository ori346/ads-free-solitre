# Ad-Free Solitaire (Klondike)

[![License: MIT](https://img.shields.io/badge/License-MIT-emerald.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF)](build.gradle.kts)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](app/build.gradle.kts)

A private, 100% offline Klondike Solitaire app for Android, built with **Kotlin 2.0** and **Jetpack Compose (Material 3)**.
Designed for zero distractions: **no ads, no login, no analytics, no trackers, and no `INTERNET` permission**. Every game, setting, and high score stays strictly on your device.

---

## Highlights & Features

- **Verified Solvable Deals & Interactive Board Solver:**
  - Every new deal in both **Draw-1** and **Draw-3** is vetted by a two-pass omniscient best-first solver (`SolitaireSolver`) with structural deadlock and cyclic-blockage detection.
  - **Interactive Solver Assistant:** Stuck mid-game? Tap **Solver** in the bottom control bar to analyze whether the current board configuration is still winnable (up to 250,000 states / 5.5s budget).
  - **Step-by-Step Solution Playback:** If a board is solvable, you can keep playing on your own or watch the solver play out the compacted winning move sequence step by step with smooth card flights (watching the solution records the game as a loss).
  - **Return to Last Solvable Point & Fatal Move Replay:** If a board becomes unsolvable, `SolitaireSolver.findLastSolvablePoint` pinpoints the exact move in your undo history that broke solvability—using binary search over stock-cycle equivalence segments, background solvability probe caching, and bitmask/multi-card stack deadlock pruning. Selecting **Return to Solvable Point** rewinds the undo history without recording a loss or breaking your win streak, highlights and animates the fatal move forward, and glides the card(s) back to the last solvable configuration.
  - **Restart Current Deal:** Replay the exact initial card layout from the beginning whenever you want a second attempt at a deal.
- **Smart One-Tap & Forgiving Drag-and-Drop:**
  - Tap any card—or tap anywhere on a movable face-up stack—to automatically send it to the highest-scoring legal destination (`TapTargetScorer`), prioritizing foundations, uncovering face-down cards, extending anchored runs, and avoiding burying foundation-needed cards.
  - **Repeated-Tap Destination Cycling (`TapCycleTracker`):** Tapping the same card or stack consecutively cycles through every legal alternative destination before wrapping around.
  - **Forgiving Drag-and-Drop:** Highlights legal target piles while dragging, snaps onto nearby valid targets, cancels cleanly when dragged back to the source stack, and glides released cards smoothly back into place.
  - Invalid taps trigger a gentle card-shake indicator, with optional tactile haptic feedback.
- **Custom Vector Art & Adaptive Launcher Icon:**
  - Custom vector emblem (`SolitaireLogo`) matching the Android Adaptive Icon, round icon, and Android 13+ monochrome themed icon.
  - Crisp SVG suit pips (`CardSuitPaths`), diamond-lattice filigree card backs (`CardBackCanvas`) in 4 colorways (*Royal Blue, Crimson, Emerald, Amethyst*), and custom-illustrated Jack, Queen, and King court portraits (`RoyalPortraitCanvas`).
- **Ergonomic Controls & Universal Device Compatibility:**
  - **Left-Handed & Right-Handed Modes:** Mirror the Stock/Waste deck to either side of the top row for effortless one-thumb reach.
  - **2-Color & 4-Color Decks:** Optional blue diamonds (♦) and green clubs (♣) for instant suit recognition.
  - **Hold-to-Repeat Undo/Redo:** Tap Undo/Redo to step one move at a time, or press and hold to rapidly rewind/fast-forward up to 500 moves.
  - **Top 10 Leaderboard & Lifetime Stats:** Tracks your Top 10 high scores (with draw-mode badges and dates), games played, win percentage, fastest win time, and current/best win streaks, celebrated with a falling confetti overlay (`WinConfettiOverlay`).
  - **Works Across Any Android Configuration:** Edge-to-edge display with safe-drawing cutout/gesture insets, font-scale clamping so accessibility text sizes never clip HUD controls, LTR layout lock across RTL system locales (such as Hebrew/Arabic), and aspect-ratio-capped card sizing for compact phones, tall screens, foldables, and tablets.

---

## Project Structure

```text
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat
gradle/libs.versions.toml    - centralized Gradle version catalog (AGP, Kotlin, Compose BOM, plugins)
.github/workflows/
  ci.yml                     - GitHub Actions CI/CD (test & check job gating release APK build and publish)
scripts/
  build-apk.bat              - run unit tests, build release/debug APK, and copy to AdFree-Solitaire.apk
  run-checks.bat             - run spotlessCheck, unit tests, lintDebug, and assembleRelease
  start-emulator.bat         - launch Android emulator (--hebrew / --english) and install debug APK
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/
  drawable/ic_launcher_*.xml
  mipmap-anydpi/ic_launcher*.xml
  xml/                       - explicit no-backup and data-extraction rules
app/src/main/java/com/adfree/solitaire/
  MainActivity.kt            - edge-to-edge host activity, locale/LTR enforcement, background timer pause
  GameViewModel.kt           - observable gameplay state, undo/redo, tap cycling, hints, solver, stats
  config/
    GameConfig.kt            - centralized rules, scoring, tap heuristic weights, and solver budgets
    UiConfig.kt              - centralized board layout proportions, touch thresholds, and animation timings
  data/
    Models.kt                - ThemeMode, Settings, ScoreEntry leaderboard comparator, Stats
    GameStateSerializer.kt   - cached JSON serialization for GameState, undo history, and top scores
    Storage.kt               - on-device private SharedPreferences persistence
  game/
    CardModels.kt            - Suit, Card, Pile, and Hint domain models
    GameState.kt             - immutable board snapshot and structural stock/waste/board hashes
    GameRules.kt             - Klondike draw, move legality, scoring, and stock/waste cycle iterator
    GameDealer.kt            - cryptographically shuffled dealing and solver-vetted deal generation
    TapTargetScorer.kt       - strategic destination scoring and smart tap-move resolution
    TapCycleTracker.kt       - repeated-tap alternative destination cycling state
    HintFinder.kt            - prioritized hint discovery and stock-cycle move checks
    AutoCompleter.kt         - deterministic auto-complete simulation and step execution
    SolitaireSolver.kt       - two-pass omniscient best-first Klondike solver and binary-search fatal-move finder
    SolverDeadlockDetector.kt  - bitmask structural deadlock, stack-clearability, and closed-cycle detector
    SolverSuccessorGenerator.kt - omniscient move generation and safe foundation advancement
    SolverSolutionCompactor.kt - winning move sequence replay and stock-cycle compaction
    UndoRedoHistory.kt       - generic bounded undo/redo history stack
    Engine.kt                - public game engine facade delegating to the focused game modules
  ui/
    GameScreen.kt            - top-level screen wiring HUD, responsive card board, overlays, and dialogs
    CardView.kt              - card face/back composition and empty pile slot rendering
    theme/
      SolitaireTheme.kt      - centralized color palette, card back styles, font-scale clamp, and M3 theme
    components/
      SolitaireLogo.kt       - vector emblem matching the adaptive launcher icon
      CardSuitPaths.kt       - SVG suit paths and 2/4-color ink mapping
      RoyalPortraitCanvas.kt - Jack, Queen, and King court portrait Canvas illustrations
      CardBackCanvas.kt      - diamond-lattice filigree card back rendering
    board/
      BoardGestureState.kt   - single-pointer drag/tap recognition, card flights, and shake feedback
      TopPlayRow.kt          - foundations, fanned waste pile, and stock slot with counter badge
      TableauGrid.kt         - 7-column tableau grid with adaptive stack compression and flight/drag overlays
    hud/
      TopHudBar.kt           - score/moves/time pill and Scores/Help/Settings action buttons
      BottomControlPill.kt   - Undo/Redo (hold-to-repeat), New, Hint, Solver, and Auto-Complete controls
      WinConfettiOverlay.kt  - victory confetti particles and transient status banners
    dialogs/
      ConfirmDealDialog.kt   - New Deal / Restart Deal confirmation prompt
      SolverDialog.kt        - interactive solvability check, Watch Solution, and Return to Solvable Point dialog
      WinDialog.kt           - victory summary, rank badge, and Play Again actions
      HelpDialog.kt          - built-in Klondike rules, controls, solver, and scoring guide
      StatsDialog.kt         - Top 10 leaderboard and lifetime statistics summary
      SettingsDialog.kt      - draw mode, 4-color deck, theme, handedness, solver toggle, haptics, card backs
app/src/test/java/com/adfree/solitaire/
  game/
    EngineTest.kt            - core Klondike rules, hints, and auto-complete unit tests
    SolverTest.kt            - omniscient solver replay, compaction, deadlock pruning, and binary-search fatal-move tests
    TapAndScoringTest.kt     - tap-to-move destination ranking, cycle tracking, and drop-target tests
    RulesAndInvariantsTest.kt - randomized property simulations and custom scoring config tests
  data/
    StateManagementTest.kt   - UndoRedoHistory, serializer, HUD formatting, leaderboard, and Stats tests
```

---

## Install on Your Android Phone

1. Download [`AdFree-Solitaire.apk` from the Latest GitHub Release](https://github.com/ori346/ads-free-solitre/releases/tag/latest) to your Android phone.
2. Tap `AdFree-Solitaire.apk` on your phone to install it (allow *"Install unknown apps"* if prompted by Android).

---

## Building, Formatting & Running Checks

### Run All Checks (Formatting, Unit Tests, Android Lint & Release APK)
```powershell
.\scripts\run-checks.bat
```
Or invoke Gradle directly:
```powershell
.\gradlew.bat spotlessCheck testDebugUnitTest testReleaseUnitTest lintDebug assembleRelease
```

### Auto-Format Code (Spotless + ktlint)
```powershell
.\gradlew.bat spotlessApply
```

### Build APK (Runs Unit Tests First)
```powershell
.\scripts\build-apk.bat
```
The signed release APK is generated at `app/build/outputs/apk/release/app-release.apk` and copied to `AdFree-Solitaire.apk` in the project root (pass `--skip-tests` to skip unit tests).

---

## License

Released under the [MIT License](LICENSE).
