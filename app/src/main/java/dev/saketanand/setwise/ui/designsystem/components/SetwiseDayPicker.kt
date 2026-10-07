package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Pick days of the week (Mon … Sun), any number: training days in onboarding and Settings.
 * With [onNoneClick], a last "None" chip ([noneSelected]) says there are no fixed days. Chips wrap
 * onto a second line on narrow screens or large text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetwiseDayPicker(
    selected: ImmutableSet<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    noneSelected: Boolean = false,
    /** Shows the "None" chip; null hides it. */
    onNoneClick: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The app's week runs Monday to Sunday (util/Weeks.kt).
        DayOfWeek.entries.forEach { day ->
            SetwiseFilterChip(
                label = day.getDisplayName(TextStyle.SHORT, locale),
                selected = day in selected,
                onClick = { onToggle(day) },
            )
        }
        if (onNoneClick != null) {
            SetwiseFilterChip(label = stringResource(R.string.no_fixed_days), selected = noneSelected, onClick = onNoneClick)
        }
    }
}

@PreviewComponents
@Composable
private fun SetwiseDayPickerPreview() = SetwisePreview {
    SetwiseDayPicker(selected = persistentSetOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), onToggle = {}, onNoneClick = {})
}

@PreviewComponents
@Composable
private fun SetwiseDayPickerNonePreview() = SetwisePreview {
    SetwiseDayPicker(selected = persistentSetOf(), onToggle = {}, noneSelected = true, onNoneClick = {})
}
