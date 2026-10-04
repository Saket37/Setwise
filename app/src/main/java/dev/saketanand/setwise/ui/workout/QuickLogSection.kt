package dev.saketanand.setwise.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.ai.QuickLogResult
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseFilterChip
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseInputBar
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.util.toWeightLabel
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer

/** Lines to tap in "Try saying" (they fill the bar). */
private val EXAMPLES = listOf("bench 3x8 at 60", "same as last time", "treadmill 30 min 6% incline")

/**
 * Design "Quick log": the bar at the bottom of the workout, with the "Understood as" card above
 * it once a line is read, why it wasn't understood, or "Try saying" while the bar is empty and
 * focused.
 * @param onSpeak the mic; null hides it (no speech input on this phone).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLogSection(
    field: TextFieldState,
    quickLog: QuickLogUi,
    isFieldFocused: Boolean,
    onFieldFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onAction: (ActiveWorkoutAction) -> Unit,
    onSpeak: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val preview = quickLog.preview
        when {
            quickLog.isListening -> ListeningRow(heard = quickLog.heard)
            preview != null -> QuickLogCard(preview = preview, onAction = onAction)
            quickLog.micProblem != null -> Text(
                text = stringResource(if (quickLog.micProblem == MicProblem.Failed) R.string.quick_log_mic_failed else R.string.quick_log_nothing_heard),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            quickLog.problem != null -> Text(
                text = stringResource(quickLog.problem.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            isFieldFocused && field.text.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.quick_log_try_saying).uppercase(currentLocale()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEach { example ->
                        SetwiseFilterChip(label = example, selected = false, onClick = { field.edit { replace(0, length, example) } })
                    }
                }
            }
        }
        SetwiseInputBar(
            state = field,
            placeholder = stringResource(R.string.quick_log_placeholder),
            onSubmit = { onAction(ActiveWorkoutAction.OnQuickLogSubmit(field.text.toString())) },
            leadingIcon = R.drawable.ic_ai_sparkle,
            focusRequester = focusRequester,
            onFocusChange = onFieldFocusChange,
        ) {
            if (onSpeak != null) {
                // Listening: the mic becomes Stop, filled so it reads as on.
                SetwiseIconButton(
                    icon = if (quickLog.isListening) R.drawable.ic_stop else R.drawable.ic_mic,
                    contentDescription = stringResource(if (quickLog.isListening) R.string.quick_log_stop_listening else R.string.quick_log_speak),
                    onClick = onSpeak,
                    size = 44.dp,
                    iconSize = 20.dp,
                    colors = if (quickLog.isListening) {
                        SetwiseIconButtonDefaults.filledColors()
                    } else {
                        SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
            if (quickLog.isReading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(20.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                SetwiseIconButton(
                    icon = R.drawable.ic_send,
                    contentDescription = stringResource(R.string.quick_log_send),
                    onClick = { onAction(ActiveWorkoutAction.OnQuickLogSubmit(field.text.toString())) },
                    enabled = field.text.isNotBlank(),
                    size = 44.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }
}

/** "● Listening…" and what's been heard so far (or what to say). */
@Composable
private fun ListeningRow(heard: String) {
    val pulse = rememberInfiniteTransition(label = "listening")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.quick_log_listening), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = heard.ifEmpty { stringResource(R.string.quick_log_listening_hint) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (heard.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "✦ Understood as · Check before adding", the exercise, its sets (or cardio), Edit / Add. */
@Composable
private fun QuickLogCard(preview: QuickLogPreview, onAction: (ActiveWorkoutAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_ai_sparkle), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.quick_log_understood),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(stringResource(R.string.quick_log_check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.exerciseName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = listOfNotNull(
                    preview.matchedFrom?.let { stringResource(R.string.quick_log_matched, it) } ?: stringResource(R.string.quick_log_open_exercise),
                    stringResource(if (preview.isInWorkout) R.string.quick_log_in_workout else R.string.quick_log_adds_exercise),
                    stringResource(R.string.quick_log_by_model).takeIf { preview.byModel },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        preview.cardio?.let { cardio ->
            Text(cardio.summary(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        preview.sets.forEachIndexed { index, set -> QuickLogSetRow(number = index + 1, set = set) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetwiseButton(
                text = stringResource(R.string.quick_log_edit),
                onClick = { onAction(ActiveWorkoutAction.OnQuickLogEdit) },
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
            SetwiseButton(
                text = if (preview.cardio != null) {
                    stringResource(R.string.log_cardio)
                } else {
                    pluralStringResource(R.plurals.quick_log_add_sets, preview.sets.size, preview.sets.size)
                },
                onClick = { onAction(ActiveWorkoutAction.OnQuickLogConfirm) },
                size = SetwiseButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "1   40 kg   6 reps" (or "45 s" for a hold). */
@Composable
private fun QuickLogSetRow(number: Int, set: SetFact) {
    val locale = currentLocale()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
        set.weightKg?.let { ValueWithUnit(it.toWeightLabel(locale), stringResource(R.string.unit_kg)) }
        set.reps?.let { ValueWithUnit(it.toString(), stringResource(R.string.unit_reps)) }
        set.seconds?.let { ValueWithUnit(it.toString(), stringResource(R.string.unit_seconds)) }
    }
}

@Composable
private fun ValueWithUnit(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.numberMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
    }
}

private fun QuickLogResult.Reason.messageRes() = when (this) {
    QuickLogResult.Reason.NoExercise -> R.string.quick_log_no_exercise
    QuickLogResult.Reason.NothingToLog -> R.string.quick_log_nothing
    QuickLogResult.Reason.NoLastTime -> R.string.quick_log_no_last_time
}

@ComponentPreviews
@Composable
private fun QuickLogSectionPreview() = SetwisePreview(padding = 0.dp) {
    QuickLogSection(
        field = rememberTextFieldState("ohp 3 sets of 6 at 40, last one 37.5 for 8"),
        quickLog = QuickLogUi(
            preview = QuickLogPreview(
                exerciseName = "Overhead Press (Barbell)",
                matchedFrom = "ohp",
                isInWorkout = true,
                kind = SetKind.WeightReps,
                sets = listOf(SetFact(40.0, 6, null), SetFact(40.0, 6, null), SetFact(37.5, 8, null)),
                byModel = true,
            ),
        ),
        isFieldFocused = false,
        onFieldFocusChange = {},
        focusRequester = remember { FocusRequester() },
        onAction = {},
        onSpeak = {},
    )
}
