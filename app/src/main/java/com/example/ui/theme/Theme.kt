package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class WinnerExtendedColors(
    val brandTitleYellow: Color,
    val brandTitleBorderYellow: Color,
    val brandPlaqueStart: Color,
    val brandPlaqueMid: Color,
    val brandPlaqueEnd: Color,
    val brandTitleBlue: Color,
    val bkashAccent: Color,
    val onBkashAccent: Color,
    val bkashContainer: Color,
    val onBkashContainer: Color,
    val approvedBg: Color,
    val approvedFg: Color,
    val pendingBg: Color,
    val pendingFg: Color,
    val processingBg: Color,
    val processingFg: Color,
    val rejectedBg: Color,
    val rejectedFg: Color,
    val heroCardStart: Color,
    val heroCardEnd: Color,
    val heroGold: Color,
    val heroOnCard: Color,
    val heroMutedOnCard: Color,
)

val LocalWinnerColors = staticCompositionLocalOf {
    WinnerExtendedColors(
        brandTitleYellow = WinnerBrandYellow,
        brandTitleBorderYellow = WinnerBrandYellowBorder,
        brandPlaqueStart = WinnerBrandPlaqueStart,
        brandPlaqueMid = WinnerBrandPlaqueMid,
        brandPlaqueEnd = WinnerBrandPlaqueEnd,
        brandTitleBlue = WinnerBrandBlueLight,
        bkashAccent = BkashRoseLight,
        onBkashAccent = OnBkashRoseLight,
        bkashContainer = BkashRoseContainerLight,
        onBkashContainer = OnBkashRoseContainerLight,
        approvedBg = StatusApprovedBgLight,
        approvedFg = StatusApprovedFgLight,
        pendingBg = StatusPendingBgLight,
        pendingFg = StatusPendingFgLight,
        processingBg = StatusProcessingBgLight,
        processingFg = StatusProcessingFgLight,
        rejectedBg = StatusRejectedBgLight,
        rejectedFg = StatusRejectedFgLight,
        heroCardStart = ExecutiveCardGradientStart,
        heroCardEnd = ExecutiveCardGradientEnd,
        heroGold = ExecutiveGoldAccent,
        heroOnCard = ExecutiveLightText,
        heroMutedOnCard = ExecutiveMutedText,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = BangkokNavyPrimaryLight,
    onPrimary = OnBangkokNavyLight,
    primaryContainer = BangkokNavyContainerLight,
    onPrimaryContainer = OnBangkokNavyContainerLight,
    secondary = EmeraldSecondaryLight,
    onSecondary = OnEmeraldSecondaryLight,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    tertiary = RoyalGoldTertiaryLight,
    onTertiary = OnRoyalGoldTertiaryLight,
    tertiaryContainer = RoyalGoldContainerLight,
    onTertiaryContainer = OnRoyalGoldContainerLight,
    background = PorcelainBackgroundLight,
    onBackground = OnPorcelainBackgroundLight,
    surface = SurfacePureLight,
    onSurface = OnPorcelainBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorCrimsonLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = RoyalGoldPrimaryDark,
    onPrimary = OnRoyalGoldPrimaryDark,
    primaryContainer = RoyalGoldContainerDark,
    onPrimaryContainer = OnRoyalGoldContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = OnEmeraldSecondaryDark,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = SkyBlueTertiaryDark,
    onTertiary = OnSkyBlueTertiaryDark,
    tertiaryContainer = SkyBlueContainerDark,
    onTertiaryContainer = OnSkyBlueContainerDark,
    background = MidnightBackgroundDark,
    onBackground = OnMidnightBackgroundDark,
    surface = ObsidianSurfaceDark,
    onSurface = OnMidnightBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorCrimsonDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

private val LightExtendedColors = WinnerExtendedColors(
    brandTitleYellow = WinnerBrandYellow,
    brandTitleBorderYellow = WinnerBrandYellowBorder,
    brandPlaqueStart = WinnerBrandPlaqueStart,
    brandPlaqueMid = WinnerBrandPlaqueMid,
    brandPlaqueEnd = WinnerBrandPlaqueEnd,
    brandTitleBlue = WinnerBrandBlueLight,
    bkashAccent = BkashRoseLight,
    onBkashAccent = OnBkashRoseLight,
    bkashContainer = BkashRoseContainerLight,
    onBkashContainer = OnBkashRoseContainerLight,
    approvedBg = StatusApprovedBgLight,
    approvedFg = StatusApprovedFgLight,
    pendingBg = StatusPendingBgLight,
    pendingFg = StatusPendingFgLight,
    processingBg = StatusProcessingBgLight,
    processingFg = StatusProcessingFgLight,
    rejectedBg = StatusRejectedBgLight,
    rejectedFg = StatusRejectedFgLight,
    heroCardStart = ExecutiveCardGradientStart,
    heroCardEnd = ExecutiveCardGradientEnd,
    heroGold = ExecutiveGoldAccent,
    heroOnCard = ExecutiveLightText,
    heroMutedOnCard = ExecutiveMutedText,
)

private val DarkExtendedColors = WinnerExtendedColors(
    brandTitleYellow = WinnerBrandYellow,
    brandTitleBorderYellow = WinnerBrandYellowBorder,
    brandPlaqueStart = WinnerBrandPlaqueStart,
    brandPlaqueMid = WinnerBrandPlaqueMid,
    brandPlaqueEnd = WinnerBrandPlaqueEnd,
    brandTitleBlue = WinnerBrandBlueDark,
    bkashAccent = BkashRoseDark,
    onBkashAccent = OnBkashRoseDark,
    bkashContainer = BkashRoseContainerDark,
    onBkashContainer = OnBkashRoseContainerDark,
    approvedBg = StatusApprovedBgDark,
    approvedFg = StatusApprovedFgDark,
    pendingBg = StatusPendingBgDark,
    pendingFg = StatusPendingFgDark,
    processingBg = StatusProcessingBgDark,
    processingFg = StatusProcessingFgDark,
    rejectedBg = StatusRejectedBgDark,
    rejectedFg = StatusRejectedFgDark,
    heroCardStart = ExecutiveCardGradientStart,
    heroCardEnd = ExecutiveCardGradientEnd,
    heroGold = ExecutiveGoldAccent,
    heroOnCard = ExecutiveLightText,
    heroMutedOnCard = ExecutiveMutedText,
)

val WinnerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WinnerBalanceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWinnerColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WinnerShapes,
            content = content,
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    WinnerBalanceTheme(darkTheme = darkTheme, content = content)
}
