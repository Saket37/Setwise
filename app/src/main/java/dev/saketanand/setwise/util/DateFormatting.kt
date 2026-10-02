package dev.saketanand.setwise.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Short day label for headers, e.g. "Fri, 2 Oct".
 * Day and month names follow [locale] the order stays fixed.
 */
fun LocalDate.toShortDayLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
