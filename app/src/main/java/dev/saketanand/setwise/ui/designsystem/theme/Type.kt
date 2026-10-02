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

/*
 * The Setwise type scale. The design canvas uses ONLY these styles (see the brand board's
 * "Type scale" table), so a design value maps to exactly one style here.
 *
 * Barlow Condensed (numbers, display; tabular figures) · DM Sans (UI text).
 */
val SetwiseTypography = Typography(
    // Barlow: big numbers and titles
    /** Big number inputs: cardio duration "32:00". */
    displayLarge = barlow(56, 60),
    /** Workout name on the Summary screen. */
    displayMedium = barlow(44, 48),
    /** Spare display size. */
    displaySmall = barlow(36, 40),
    /** Screen titles ("History", "Settings", "Ready to train?") and the first-run headline. */
    headlineLarge = barlow(40, 44, weight = FontWeight.SemiBold),
    /** Sheet titles ("Start a workout"), rest-timer countdown. */
    headlineMedium = barlow(32, 36),
    /** Session clock, large stat values (Summary tiles, 1RM, incline). */
    headlineSmall = barlow(28, 32),

    // DM Sans: UI text
    /** Top-bar titles ("Add exercise", "Treadmill"). */
    titleLarge = dmSans(20, 26, FontWeight.SemiBold),
    /** Card titles, exercise names, section headers ("Templates"). */
    titleMedium = dmSans(16, 22, FontWeight.SemiBold),
    /** Small bold labels and compact actions ("Finish", "Change"). */
    titleSmall = dmSans(14, 20, FontWeight.SemiBold),
    /** Paragraphs (LLM summaries), text inputs, list-row text. */
    bodyLarge = dmSans(15, 22, FontWeight.Normal),
    /** Secondary text: "Last: 60 × 8", subtitles, meta lines. */
    bodyMedium = dmSans(13, 18, FontWeight.Normal),
    /** Captions and footnotes. */
    bodySmall = dmSans(12, 16, FontWeight.Normal),
    /** Secondary / outlined buttons ("Add exercise", "Save as template"). Same as [button]. */
    labelLarge = dmSans(15, 20, FontWeight.SemiBold),
    /** Chips, badges ("PR · Weight"), bottom-bar labels. */
    labelMedium = dmSans(12, 16, FontWeight.SemiBold),
    /** UPPERCASE captions: "PREVIOUS", "REST", "TEMPLATES". */
    labelSmall = dmSans(11, 16, FontWeight.SemiBold, letterSpacing = 0.8),
)

/*
 * Styles that Material's scale doesn't have. Use as MaterialTheme.typography.<name>.
 */

private val WordmarkStyle = barlow(26, 30, FontWeight.Bold)
private val NumberLargeStyle = barlow(26, 30)
private val NumberMediumStyle = barlow(22, 26)
private val NumberSmallStyle = barlow(18, 22)
private val ButtonLargeStyle = dmSans(17, 24, FontWeight.Bold)

/** "Setwise" next to the logo. Barlow Condensed Bold 26. */
val Typography.wordmark: TextStyle get() = WordmarkStyle

/** Stat tiles ("3", "3h 24m"), day numbers in History. Barlow 26. */
val Typography.numberLarge: TextStyle get() = NumberLargeStyle

/** Set values and set numbers ("62.5", "8", "1"), PR values. Barlow 22. */
val Typography.numberMedium: TextStyle get() = NumberMediumStyle

/** Inline numbers: "6:42 PM", "4 × 5", "100 × 5". Barlow 18. */
val Typography.numberSmall: TextStyle get() = NumberSmallStyle

/** Primary full-width 56dp buttons: "Start workout", "Log cardio", "Done". DM Sans Bold 17. */
val Typography.buttonLarge: TextStyle get() = ButtonLargeStyle

/** Secondary / outlined buttons. Alias of labelLarge (DM Sans SemiBold 15). */
val Typography.button: TextStyle get() = labelLarge
