package dev.saketanand.setwise.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.saketanand.setwise.R

/** Numbers, timers and display text. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/**
 * DM Sans for a given optical size. DM Sans is a variable font with a weight axis and an
 * optical-size axis ("opsz", 9–40). Browsers set opsz to the font size automatically, so to
 * match the design each text size gets its own family with opsz = that size.
 */
@OptIn(ExperimentalTextApi::class)
private fun dmSansFamily(opticalSize: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            resId = R.font.dm_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize.coerceIn(9, 40).toFloat()),
            ),
        )
    }
)

/** UI text at the default optical size (14). Prefer the typography styles below. */
val DmSans = dmSansFamily(opticalSize = 14)

/** Tabular figures keep ticking timers and set values from shifting sideways. */
private const val TabularNumbers = "tnum"

/**
 * Settings that make Compose lay text out like the browser the design was drawn in:
 * text centred in its line height, and no extra font padding above/below.
 */
private val DesignMatchedTextStyle = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun barlow(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.SemiBold) =
    DesignMatchedTextStyle.copy(
        fontFamily = BarlowCondensed,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontFeatureSettings = TabularNumbers,
    )

private fun dmSans(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) =
    DesignMatchedTextStyle.copy(
        fontFamily = dmSansFamily(opticalSize = size),
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

val SetwiseTypography = Typography(
    // Session clock, rest timer
    displayLarge = barlow(64, 64),
    displayMedium = barlow(44, 48),
    displaySmall = barlow(36, 40),
    // Screen titles ("History"), workout names, set values
    headlineLarge = barlow(40, 44),
    headlineMedium = barlow(32, 36),
    headlineSmall = barlow(24, 28),
    // Exercise names, section headers
    titleLarge = dmSans(22, 28, FontWeight.SemiBold),
    titleMedium = dmSans(16, 24, FontWeight.SemiBold),
    titleSmall = dmSans(14, 20, FontWeight.SemiBold),
    bodyLarge = dmSans(16, 24, FontWeight.Normal),
    bodyMedium = dmSans(14, 20, FontWeight.Normal),
    bodySmall = dmSans(12, 16, FontWeight.Normal),
    labelLarge = dmSans(14, 20, FontWeight.Medium),
    labelMedium = dmSans(12, 16, FontWeight.SemiBold),
    // Uppercase captions: "PREVIOUS", "REST", month headers
    labelSmall = dmSans(11, 16, FontWeight.SemiBold, letterSpacing = 0.8),
)

/** "Setwise" wordmark in the app header (design: Barlow Condensed Bold 26). */
private val WordmarkStyle = barlow(26, 30, FontWeight.Bold)

/** Use as `MaterialTheme.typography.wordmark`. Only for the brand name next to the logo. */
val Typography.wordmark: TextStyle get() = WordmarkStyle
