package dev.saketanand.setwise.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Weight without the unit and without needless decimals: 60.0 → "60", 62.5 → "62.5",
 * 1.25 → "1.25". Uses the locale's decimal separator ("62,5" in German) and no grouping,
 * so it reads like what the user typed.
 */
fun Double.toWeightLabel(locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(this)

/**
 * Weight for a text field the user edits: always ASCII digits and "." ("62.5"), so it can be
 * parsed back whatever the phone's locale (some locales use other digits, e.g. "٦٢٫٥").
 * For read-only labels use [toWeightLabel].
 */
fun Double.toWeightInput(): String = toWeightLabel(Locale.ROOT)
