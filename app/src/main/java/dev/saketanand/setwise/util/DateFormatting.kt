package dev.saketanand.setwise.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Short day label for headers, e.g. "Fri, 2 Oct".
 * Day and month names follow [locale] the order stays fixed.
 */
fun LocalDate.toShortDayLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))

/** Time of day in the user's locale style, e.g. "6:42 PM" or "18:42". */
fun LocalTime.toShortTimeLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
