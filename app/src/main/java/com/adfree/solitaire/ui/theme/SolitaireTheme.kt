package com.adfree.solitaire.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private const val MIN_UI_FONT_SCALE = 0.85f
private const val MAX_UI_FONT_SCALE = 1.15f

/** Centralized color palette for table felt, cards, HUD chrome, court portraits, and branding. */
object SolitaireColors {
    val TableLight = Color(0xFF1B6B3A)
    val TableDark = Color(0xFF0E2A1A)
    val PrimaryLight = Color(0xFF1B6B3A)
    val PrimaryDark = Color(0xFF81C784)

    val HighlightGold = Color(0xFFFFD600)
    val HighlightFill = Color(0x33FFD600)

    val EmptySlotFill = Color(0x24000000)
    val EmptySlotBorder = Color(0x4DFFFFFF)
    val EmptySlotSuitTint = Color(0x55FFFFFF)
    val EmptySlotLabelTint = Color(0x77FFFFFF)

    val HudPillLight = Color(0x730A2E17)
    val HudPillDark = Color(0x990A1E12)
    val HudPillBorder = Color(0x29FFFFFF)
    val HudLabelText = Color(0xADFFFFFF)
    val HudHelpIconTint = Color(0xE6FFFFFF)

    val BottomPillLight = Color(0xB30B351B)
    val BottomPillDark = Color(0xCC08190F)
    val BottomPillBorder = Color(0x2EFFFFFF)
    val BottomPillDivider = Color(0x29FFFFFF)
    val AutoCompleteContent = Color(0xFF111111)

    val BannerBg = Color(0xDD000000)
    val StockCountBadgeBg = Color(0xB3000000)

    val SuitBlack = Color(0xFF141414)
    val SuitRed = Color(0xFFD91B24)
    val SuitBlue = Color(0xFF0057D9)
    val SuitGreen = Color(0xFF0B7A3E)

    val CardFaceLight = Color.White
    val CardFaceDark = Color(0xFFF4F4F4)
    val CardBorderLight = Color(0x77000000)
    val CardBorderDark = Color(0x99000000)
    val CardBackFrameBg = Color(0xFFFAFAFA)

    val CardBackRoyalBlueBase = Color(0xFF1E4FA8)
    val CardBackRoyalBlueAccent = Color(0xFF3B6FD0)
    val CardBackCrimsonBase = Color(0xFFB71C1C)
    val CardBackCrimsonAccent = Color(0xFFD84343)
    val CardBackEmeraldBase = Color(0xFF2E7D32)
    val CardBackEmeraldAccent = Color(0xFF4CAF50)
    val CardBackAmethystBase = Color(0xFF5E35B1)
    val CardBackAmethystAccent = Color(0xFF8561D6)

    val ConfettiColors: List<Color> = listOf(
        Color(0x99FFD600),
        Color(0x99FF5252),
        Color(0x9969F0AE),
        Color(0x9940C4FF),
        Color(0x99E040FB),
        Color(0x99FFFFFF),
    )

    val RoyalCanvasBg = Color(0xFFFFF9EA)
    val RoyalHalo = Color(0x22E5A918)
    val RoyalGold = Color(0xFFE5A918)
    val RoyalGoldDark = Color(0xFF9E6B06)
    val RoyalCrimson = Color(0xFFC62828)
    val RoyalBlue = Color(0xFF1558B0)
    val RoyalNavy = Color(0xFF0D2B52)
    val RoyalSkin = Color(0xFFFBE2C4)
    val RoyalSkinOutline = Color(0xFF5D4037)
    val RoyalHairGold = Color(0xFFD99B26)
    val RoyalVeil = Color(0xFFF2E8D5)
    val RoyalDarkInk = Color(0xFF1B1B1B)

    val LogoOuterEmerald = Color(0xFF0E4425)
    val LogoInnerEmerald = Color(0xFF1B6B3A)
    val LogoGoldRim = Color(0x66FFD600)
    val LogoBackCardFill = Color(0xFFF5F5F5)
    val LogoBackCardBorder = Color(0xFFCCCCCC)
    val LogoHeartRed = Color(0xFFD32F2F)
    val LogoFrontCardShadow = Color(0x44000000)
    val LogoSpadeBlack = Color(0xFF151515)
}

/** Pairs of primary and filigree accent colors for selectable card back styles. */
val CardBacks: List<Pair<Color, Color>> = listOf(
    SolitaireColors.CardBackRoyalBlueBase to SolitaireColors.CardBackRoyalBlueAccent,
    SolitaireColors.CardBackCrimsonBase to SolitaireColors.CardBackCrimsonAccent,
    SolitaireColors.CardBackEmeraldBase to SolitaireColors.CardBackEmeraldAccent,
    SolitaireColors.CardBackAmethystBase to SolitaireColors.CardBackAmethystAccent,
)

/** Display names corresponding 1-to-1 with [CardBacks]. */
val CardBackNames: List<String> = listOf("Royal Blue", "Crimson", "Emerald", "Amethyst")

/**
 * Applies the Ad-Free Solitaire Material 3 color scheme, forces left-to-right card layout,
 * and clamps extreme system font-scale values so HUD pills and buttons never clip.
 */
@Composable
fun SolitaireTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(primary = SolitaireColors.PrimaryDark)
    } else {
        lightColorScheme(primary = SolitaireColors.PrimaryLight)
    }
    val currentDensity = LocalDensity.current
    val clampedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE),
    )
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDensity provides clampedDensity,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
