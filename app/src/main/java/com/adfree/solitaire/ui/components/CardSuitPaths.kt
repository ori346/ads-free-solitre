package com.adfree.solitaire.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import com.adfree.solitaire.game.Suit
import com.adfree.solitaire.ui.theme.SolitaireColors

// Exact SVG suit paths from David Bellot / htdebeer SVG-cards
// (upright orientation: tip at y=-7.94, base at y=+7.94)
val SpadePath: Path by lazy {
    PathParser().parsePathString(
        "M3.92,-7.93c-1.67,0-2.55,1.78-3.01,3.56c-0.46,1.78-0.5,3.56-0.5,3.56" +
            "L-0.44,-0.8c0,0-0.16-7.13-3.5-7.13H3.92z" +
            "M6.75,-0.8C6.74,2.67,1.53,5.19,0,7.93C-1.53,5.18-6.74,2.66-6.75,-0.8" +
            "c0-1.64,1.51-2.97,3.38-2.97C-1.51,-3.77,0,-2.44,0,-0.8" +
            "c0,-1.64,1.51,-2.97,3.38,-2.97S6.75,-2.44,6.75,-0.8z",
    ).toPath()
}

val HeartPath: Path by lazy {
    PathParser().parsePathString(
        "M-7.67,3.93c0.01-4.71,5.91-8.14,7.64-11.87c1.75,3.72,7.66,7.12,7.7,11.83" +
            "c0.01,2.22-1.71,4.03-3.82,4.03C1.73,7.92,0,6.13,0,3.91" +
            "c0,2.23-1.71,4.03-3.82,4.03C-5.94,7.95-7.66,6.15-7.67,3.93z",
    ).toPath()
}

val DiamondPath: Path by lazy {
    PathParser().parsePathString(
        "M-7.68,0C-6.57,-0.035 -5.622,-0.7413 -4.776,-1.393C-3.281,-2.648 -1.987,-4.141 -0.895,-5.757" +
            "C-0.494,-6.426 -0.022,-7.135 0,-7.94C0.038,-6.758 0.758,-5.734 1.438,-4.82" +
            "C2.65,-3.310 4.086,-1.974 5.666,-0.857C6.28,-0.474 6.932,-0.0185 7.68,0" +
            "C6.57,0.035 5.622,0.741 4.776,1.392C3.281,2.648 1.987,4.141 0.895,5.757" +
            "C0.494,6.426 0.0225,7.135 0,7.94C-0.0144,6.936 -0.587,6.032 -1.132,5.228" +
            "C-2.3,3.63 -3.779,2.264 -5.358,1.0728C-6.061,0.605 -6.8053,0.039 -7.68,0Z",
    ).toPath()
}

val ClubPath: Path by lazy {
    PathParser().parsePathString(
        "M3.35,4.66c0-1.81-1.51-3.28-3.38-3.28s-3.38,1.47-3.38,3.28" +
            "c0,1.81,1.51,3.28,3.38,3.28S3.35,6.47,3.35,4.66z" +
            "M7.71,-1.55c0-1.81-1.51-3.28-3.38-3.28S0.95,-3.36,0.95,-1.55" +
            "c0,1.81,1.51,3.28,3.38,3.28S7.71,0.26,7.71,-1.55z" +
            "M-0.95,-1.57c0-0.66-0.2-1.28-0.55-1.8c-0.6-0.89-1.65-1.48-2.83-1.48" +
            "c-1.87,0-3.38,1.47-3.38,3.28 c0,1.81,1.51,3.28,3.38,3.28S-0.95,0.24-0.95,-1.57z" +
            "M3.94,-7.94c-1.67,0-2.55,1.36-3.01,2.72C0.47,-3.86,0.43,-2.5,0.43,-2.5" +
            "h-0.86c0,0-0.16-5.43-3.5-5.44H3.94z" +
            "M-2.32,2.25c1.9-1.9,1.9-4.75,1.9-4.75l0.85-0.01c0,0,0,2.91,1.85,4.76" +
            "M-1.94,-3.85c1.9,1.9,4.75,1.9,4.75,1.9v0.85c0,0-2.91,0-4.76,1.85" +
            "M1.87,-3.86c-1.9,1.9-4.75,1.9-4.75,1.9v0.85c0,0,2.91,0,4.76,1.85",
    ).toPath()
}

/** Returns the vector [Path] for [suit]. */
fun suitPath(suit: Suit): Path = when (suit) {
    Suit.SPADES -> SpadePath
    Suit.HEARTS -> HeartPath
    Suit.DIAMONDS -> DiamondPath
    Suit.CLUBS -> ClubPath
}

/** Returns the display ink [Color] for [suit] in either standard 2-color or 4-color deck mode. */
fun suitColor(suit: Suit, fourColor: Boolean): Color {
    if (!fourColor) {
        return if (suit.isRed) SolitaireColors.SuitRed else SolitaireColors.SuitBlack
    }
    return when (suit) {
        Suit.SPADES -> SolitaireColors.SuitBlack
        Suit.HEARTS -> SolitaireColors.SuitRed
        Suit.DIAMONDS -> SolitaireColors.SuitBlue
        Suit.CLUBS -> SolitaireColors.SuitGreen
    }
}
