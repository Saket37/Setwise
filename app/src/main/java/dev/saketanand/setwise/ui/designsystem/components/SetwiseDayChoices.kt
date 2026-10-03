package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * "+ Log workout  [Rest] [Missed]": what happened on a past day without a workout. Used by
 * History's empty-day card and the day check-in sheet. Tapping a selected chip clears it.
 */
@Composable
fun SetwiseDayChoices(
    isRest: Boolean,
    isMissed: Boolean,
    onLogWorkout: () -> Unit,
    onRestClick: () -> Unit,
    onMissedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetwiseButton(
            text = stringResource(R.string.log_workout),
            onClick = onLogWorkout,
            style = SetwiseButtonStyle.Tonal,
            size = SetwiseButtonSize.Small,
            startIcon = R.drawable.ic_add,
        )
        SetwiseFilterChip(label = stringResource(R.string.mark_rest), selected = isRest, onClick = onRestClick)
        SetwiseFilterChip(label = stringResource(R.string.mark_missed), selected = isMissed, onClick = onMissedClick)
    }
}

@ComponentPreviews
@Composable
private fun SetwiseDayChoicesPreview() = SetwisePreview {
    SetwiseDayChoices(isRest = true, isMissed = false, onLogWorkout = {}, onRestClick = {}, onMissedClick = {})
}
