package com.fitapp.imageeditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Luxury Colour Palette ─────────────────────────────────────────────────────

val Obsidian    = Color(0xFF080808)   // deepest background
val Onyx        = Color(0xFF101010)   // card / surface
val Graphite    = Color(0xFF181818)   // surface variant
val Iron        = Color(0xFF242424)   // input backgrounds
val Steel       = Color(0xFF383838)   // dividers / outlines
val Ash         = Color(0xFF6E6E6E)   // secondary text
val Silver      = Color(0xFFA0A0A0)   // tertiary text
val Cream       = Color(0xFFF4EEE4)   // primary text
val OffWhite    = Color(0xFFFFFFFF)   // emphasis / on-primary

val Gold        = Color(0xFFC8A96E)   // primary accent
val GoldLight   = Color(0xFFE2C98C)   // lighter gold for highlights
val GoldDim     = Color(0xFF2C2210)   // gold tinted container

val Emerald     = Color(0xFF2E7D32)   // kept for "Shop" CTA (brand continuity)
val EmeraldDim  = Color(0xFF0D1F0E)   // emerald container

val ErrorRose   = Color(0xFFB85757)
val ErrorDim    = Color(0xFF1F0D0D)
val ErrorLight  = Color(0xFFE8AAAA)

// ── Colour Scheme ─────────────────────────────────────────────────────────────

private val LuxuryDarkScheme = darkColorScheme(
    primary              = Gold,
    onPrimary            = Obsidian,
    primaryContainer     = GoldDim,
    onPrimaryContainer   = GoldLight,

    secondary            = Silver,
    onSecondary          = Obsidian,
    secondaryContainer   = Iron,
    onSecondaryContainer = Cream,

    tertiary             = Color(0xFF8A7A5A),
    onTertiary           = Obsidian,

    background           = Obsidian,
    onBackground         = Cream,

    surface              = Onyx,
    onSurface            = Cream,
    surfaceVariant       = Graphite,
    onSurfaceVariant     = Ash,

    outline              = Steel,
    outlineVariant       = Iron,

    error                = ErrorRose,
    onError              = Cream,
    errorContainer       = ErrorDim,
    onErrorContainer     = ErrorLight,

    scrim                = Color(0xE6000000),
    inverseSurface       = Cream,
    inverseOnSurface     = Obsidian,
    inversePrimary       = Color(0xFF8B7145),
)

// ── Typography ────────────────────────────────────────────────────────────────
// Editorial style: tight display headings, wide-tracked all-caps labels,
// comfortable body copy. No custom font files required — system sans.

val LuxuryTypography = Typography(
    // Hero titles  — e.g. "CHOOSE YOUR\nGARMENT"
    displayLarge = TextStyle(
        fontWeight  = FontWeight.Bold,
        fontSize    = 38.sp,
        lineHeight  = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontWeight  = FontWeight.Bold,
        fontSize    = 30.sp,
        lineHeight  = 34.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 24.sp,
        lineHeight  = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Screen sub-headers
    headlineLarge = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 20.sp,
        lineHeight  = 26.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 17.sp,
        lineHeight  = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    // Top bars, card titles
    titleLarge = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 15.sp,
        lineHeight  = 20.sp,
        letterSpacing = 1.5.sp,   // wide-tracked for ALL CAPS use
    ),
    titleMedium = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 1.25.sp,
    ),
    titleSmall = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 14.sp,
        letterSpacing = 1.0.sp,
    ),
    // Buttons, tags
    labelLarge = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 2.0.sp,
    ),
    labelMedium = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 10.sp,
        lineHeight  = 13.sp,
        letterSpacing = 1.75.sp,
    ),
    labelSmall = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 9.sp,
        lineHeight  = 12.sp,
        letterSpacing = 1.5.sp,
    ),
    // Body copy
    bodyLarge = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 13.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.1.sp,
    ),
)

// ── Shapes ────────────────────────────────────────────────────────────────────
// Minimal rounding — sharp edges read as luxury / editorial

val LuxuryShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small      = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium     = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large      = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LuxuryDarkScheme,
        typography  = LuxuryTypography,
        shapes      = LuxuryShapes,
        content     = content,
    )
}
