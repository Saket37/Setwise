package dev.saketanand.setwise.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.collections.immutable.toImmutableList

/**
 * Under a day's Rest / Missed, when it's one of several unanswered days in a row (#194):
 * "42 days not logged, 6 Oct – 16 Nov" and [Mark all as rest] [Mark all as missed]. Answered
 * days in between keep their answer; only the unanswered ones are marked.
 */
@Composable
internal fun UnloggedRunActions(run: UnloggedRunUi, onMark: (DayStatus) -> Unit, modifier: Modifier = Modifier) {
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", currentLocale())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = pluralStringResource(
                R.plurals.unlogged_run,
                run.days.size,
                run.days.size,
                stringResource(R.string.month_day_range, run.from.format(dayMonth), run.to.format(dayMonth)),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetwiseButton(
                text = stringResource(R.string.mark_all_rest),
                onClick = { onMark(DayStatus.Rest) },
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Small,
            )
            SetwiseButton(
                text = stringResource(R.string.mark_all_missed),
                onClick = { onMark(DayStatus.Missed) },
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Small,
            )
        }
    }
}

// Previews: one per scenario

@PreviewComponents
@Composable
private fun UnloggedRunActionsPreview() = SetwisePreview {
    val days = generateSequence(LocalDate.of(2026, 10, 6)) { it.plusDays(1) }.takeWhile { it <= LocalDate.of(2026, 11, 16) }
    UnloggedRunActions(run = UnloggedRunUi(days.toList().toImmutableList()), onMark = {})
}
