package dev.saketanand.setwise.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.LocalBootClock
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.util.toClockLabel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Bottom bar while resting (design): progress line, −15, "REST · NEXT SET 3" over the countdown,
 * +15, Skip. Counts down by itself from [RestUi.endsAtElapsed]; the ViewModel only changes it
 * on ±15 / Skip / a new rest.
 */
@Composable
fun RestTimerBar(
    rest: RestUi,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = rememberRemainingMillis(rest.endsAtElapsed)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
    ) {
        RestProgress(fraction = if (rest.totalMillis > 0) remaining.toFloat() / rest.totalMillis else 0f)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val minus = stringResource(R.string.a11y_rest_minus_15)
            SetwiseButton(
                text = stringResource(R.string.rest_minus_15),
                onClick = { onAction(ActiveWorkoutAction.OnRestAdjust(-15)) },
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Medium,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .semantics { contentDescription = minus },
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = restLabel(rest).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    // Round up: shows 0:01 until it's really over, never 0:00 while still resting.
                    text = ((remaining + 999) / 1_000 * 1_000).milliseconds.toClockLabel(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    // Screen readers announce the time when it changes a lot (polite = not every tick).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val plus = stringResource(R.string.a11y_rest_plus_15)
            SetwiseButton(
                text = stringResource(R.string.rest_plus_15),
                onClick = { onAction(ActiveWorkoutAction.OnRestAdjust(15)) },
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Medium,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .semantics { contentDescription = plus },
            )
            SetwiseButton(
                text = stringResource(R.string.skip),
                onClick = { onAction(ActiveWorkoutAction.OnRestSkip) },
                style = SetwiseButtonStyle.Tonal,
                size = SetwiseButtonSize.Medium,
                colors = SetwiseButtonDefaults.colors(SetwiseButtonStyle.Tonal)
                    .copy(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

/** "Rest · next set 3", "Rest · next: Overhead Press" or "Rest". */
@Composable
private fun restLabel(rest: RestUi): String = when {
    rest.nextSetNumber != null -> stringResource(R.string.rest_next_set, rest.nextSetNumber)
    rest.nextExerciseName != null -> stringResource(R.string.rest_next_exercise, rest.nextExerciseName)
    else -> stringResource(R.string.rest)
}

/** 4dp line, Volt part = time left. */
@Composable
private fun RestProgress(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Milliseconds until [endsAtElapsed], updated 10× a second so the progress line moves smoothly. */
@Composable
private fun rememberRemainingMillis(endsAtElapsed: Long): Long {
    val clock = LocalBootClock.current
    var remaining by remember(endsAtElapsed) { mutableLongStateOf(endsAtElapsed - clock()) }
    LaunchedEffect(endsAtElapsed, clock) {
        while (true) {
            remaining = (endsAtElapsed - clock()).coerceAtLeast(0)
            delay(100)
        }
    }
    return remaining.coerceAtLeast(0)
}

@ComponentPreviews
@Composable
private fun RestTimerBarPreview() = SetwisePreview(padding = 0.dp) {
    RestTimerBar(
        rest = RestUi(
            endsAtElapsed = SystemClock.elapsedRealtime() + 56_000,
            totalMillis = 90_000,
            nextSetNumber = 3,
            nextExerciseName = null,
        ),
        onAction = {},
    )
}
