package com.adfree.solitaire.config

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spatial proportions, touch thresholds, and stack rendering limits for the game board.
 *
 * @property boardGap Horizontal spacing between adjacent board columns and slots.
 * @property boardVerticalPadding Vertical padding above and below the main board layout.
 * @property tableauTopPadding Space between the top play row and the tableau grid.
 * @property maxBoardHeightWidthRatio Maximum card-width cap derived from available height (`maxHeight / ratio`), keeping cards proportional on wide/foldable/landscape screens.
 * @property cardAspectRatio Height-to-width ratio of a playing card.
 * @property cardCornerRadiusFraction Card corner radius as a fraction of card width.
 * @property dropTargetMarginFraction Hit-test margin around drop targets as a fraction of card width.
 * @property touchSlopMultiplier Multiplier applied to system touch slop for drag recognition.
 * @property wasteMaxRenderedCards Maximum number of cards rendered in the waste stack.
 * @property wasteMaxFannedCards Maximum number of fanned waste slots in Draw-3 mode.
 * @property wasteFanOffsetFraction Horizontal offset per fanned waste card as a fraction of card width.
 * @property tableauFaceDownStepFraction Vertical step for face-down tableau cards as a fraction of card height.
 * @property tableauFaceUpStepFraction Vertical step for face-up tableau cards as a fraction of card height.
 * @property foundationVisibleCards Maximum number of cards rendered per foundation pile.
 */
data class BoardLayoutConfig(
    val boardGap: Dp = 2.dp,
    val boardVerticalPadding: Dp = 4.dp,
    val tableauTopPadding: Dp = 8.dp,
    val maxBoardHeightWidthRatio: Float = 5.4f,
    val cardAspectRatio: Float = 1.45f,
    val cardCornerRadiusFraction: Float = 0.085f,
    val dropTargetMarginFraction: Float = 0.45f,
    val touchSlopMultiplier: Float = 2.0f,
    val wasteMaxRenderedCards: Int = 4,
    val wasteMaxFannedCards: Int = 3,
    val wasteFanOffsetFraction: Float = 0.45f,
    val tableauFaceDownStepFraction: Float = 0.115f,
    val tableauFaceUpStepFraction: Float = 0.30f,
    val foundationVisibleCards: Int = 2,
)

/**
 * Durations, delays, and particle settings for board animations and HUD feedback.
 *
 * @property tapFlightDurationMs Duration of tap-to-move card flight in milliseconds.
 * @property drawFlightDurationMs Duration of stock draw card flight in milliseconds.
 * @property autoCompleteStepDelayMs Delay between consecutive auto-complete moves in milliseconds.
 * @property autoCompleteFlightDurationMs Duration of each auto-complete card flight in milliseconds.
 * @property solverStepDelayMs Delay between consecutive step-by-step solver playback moves in milliseconds.
 * @property solverFlightDurationMs Duration of each solver playback card flight in milliseconds.
 * @property dragDropFlightDurationMs Duration of snap flight when a dragged stack drops onto a valid target.
 * @property dragReturnFlightDurationMs Duration of return flight when a dragged stack is released off-target.
 * @property invalidShakeDurationMs Duration of the invalid tap/action shake animation in milliseconds.
 * @property invalidShakeAmplitude Horizontal shake amplitude for invalid actions.
 * @property hintDisplayDurationMs Time a highlighted hint remains visible in milliseconds.
 * @property debugBannerDurationMs Time a debug notification banner remains visible in milliseconds.
 * @property confettiCycleDurationMs Full cycle duration for victory confetti particles in milliseconds.
 * @property confettiParticleCount Number of confetti particles rendered on victory.
 * @property repeatButtonInitialDelayMs Hold delay before undo/redo repeat begins in milliseconds.
 * @property repeatButtonStepIntervalMs Interval between repeated undo/redo steps while held in milliseconds.
 */
data class AnimationConfig(
    val tapFlightDurationMs: Int = 170,
    val drawFlightDurationMs: Int = 150,
    val autoCompleteStepDelayMs: Long = 140L,
    val autoCompleteFlightDurationMs: Int = 130,
    val solverStepDelayMs: Long = 500L,
    val solverFlightDurationMs: Int = 240,
    val dragDropFlightDurationMs: Int = 125,
    val dragReturnFlightDurationMs: Int = 135,
    val invalidShakeDurationMs: Int = 180,
    val invalidShakeAmplitude: Dp = 2.dp,
    val hintDisplayDurationMs: Long = 2_200L,
    val debugBannerDurationMs: Long = 3_500L,
    val confettiCycleDurationMs: Int = 4_800,
    val confettiParticleCount: Int = 20,
    val repeatButtonInitialDelayMs: Long = 380L,
    val repeatButtonStepIntervalMs: Long = 110L,
)

/**
 * Aggregate UI layout and animation configuration.
 *
 * @property layout Board geometry, proportions, and stack display limits.
 * @property animation Animation timings and interaction repeat intervals.
 */
data class UiConfig(
    val layout: BoardLayoutConfig = BoardLayoutConfig(),
    val animation: AnimationConfig = AnimationConfig(),
)

/** Default UI layout and animation configuration. */
val DefaultUiConfig = UiConfig()
