package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Native heavy-stroke geometric fintech display font for WINNER BALANCE & balances
val BrandDisplayFontFamily = FontFamily(
    Font(R.font.russo_one, FontWeight.Normal),
)

// Stylish geometric sans-serif registered at Normal weight so FontSynthesis.All
// renders crisp, true bold strokes across all SemiBold/Bold/ExtraBold UI text
val StylishUiFontFamily = FontFamily(
    Font(R.font.poppins, FontWeight.Normal),
)

val WinnerBrandTitleStyle = TextStyle(
    fontFamily = BrandDisplayFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSynthesis = FontSynthesis.All,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = 1.0.sp,
    shadow = Shadow(
        color = Color(0xCC000000),
        offset = Offset(0f, 3f),
        blurRadius = 6f,
    ),
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.2.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.1.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = StylishUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSynthesis = FontSynthesis.All,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.35.sp,
    ),
)
