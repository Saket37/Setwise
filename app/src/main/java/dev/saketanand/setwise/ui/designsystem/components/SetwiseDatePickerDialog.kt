package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Pick a day up to [latest]: OK and Cancel, weeks from Monday like the app's (History's calendar,
 * the weekly stats; util/Weeks.kt) whatever the locale's first day is (#142).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetwiseDatePickerDialog(
    initial: LocalDate,
    latest: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestMillis = latest.utcMillis()
    // The calendar takes its first day of the week from this locale: "fw-mon" says Monday.
    val locale = LocalConfiguration.current.locales[0].withMondayFirst()
    val state = remember(locale, latestMillis) {
        DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initial.utcMillis(),
            initialDisplayedMonthMillis = initial.utcMillis(),
            yearRange = DatePickerDefaults.YearRange.first..latest.year,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latestMillis
                override fun isSelectableYear(year: Int) = year <= latest.year
            },
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SetwiseButton(
                text = stringResource(R.string.ok),
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        dismissButton = {
            SetwiseButton(text = stringResource(R.string.cancel), onClick = onDismiss, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Locale.withMondayFirst(): Locale = Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("fw", "mon").build()

@PreviewComponents
@Composable
private fun SetwiseDatePickerDialogPreview() = SetwisePreview {
    SetwiseDatePickerDialog(initial = LocalDate.of(2026, 10, 6), latest = LocalDate.of(2026, 10, 6), onConfirm = {}, onDismiss = {})
}
