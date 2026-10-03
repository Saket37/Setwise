package dev.saketanand.setwise.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseBarChart
import dev.saketanand.setwise.ui.designsystem.components.SetwiseEmptyState
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.ui.workout.SetKind
import dev.saketanand.setwise.ui.workout.label
import dev.saketanand.setwise.util.toShortDayLabel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ExerciseDetail].
 * @param onOpenWorkout A session: its workout summary.
 */
@Composable
fun ExerciseDetailScreenRoot(
    onBack: () -> Unit,
    onOpenWorkout: (workoutId: Long) -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // The exercise was deleted: nothing to show.
    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) onBack() }
    ExerciseDetailScreen(
        uiState = uiState,
        onBack = dropUnlessResumed(block = onBack),
        onSessionClick = { workoutId -> onOpenWorkout(workoutId) },
    )
}

/**
 * Design "Exercise detail": the name, a progress chart (one bar per week) and its sessions.
 * (The plateau note and next-session suggestion come with milestone 13, progression hints.)
 */
@Composable
fun ExerciseDetailScreen(
    uiState: ExerciseDetailUiState,
    onBack: () -> Unit,
    onSessionClick: (workoutId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SetwiseTopAppBar(
            title = uiState.name,
            onBack = onBack,
            subtitle = if (uiState.muscleGroup.isNotEmpty()) {
                {
                    Text(
                        text = stringResource(R.string.exercise_muscle_and_equipment, uiState.muscleGroup, uiState.equipment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        )
        when {
            uiState.isLoading || uiState.isMissing -> Unit
            uiState.progress == null -> SetwiseEmptyState(
                title = stringResource(R.string.exercise_no_sessions),
                message = stringResource(R.string.exercise_no_sessions_message),
                icon = R.drawable.ic_trend,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "progress") { ProgressCard(progress = uiState.progress) }
                item(key = "sessions-title") {
                    Text(
                        text = stringResource(R.string.recent_sessions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(uiState.sessions, key = { it.workoutId }) { session ->
                    SessionRow(session = session, kind = uiState.kind, onClick = { onSessionClick(session.workoutId) })
                }
            }
        }
    }
}

/** "Estimated 1RM · last 8 weeks   48 kg", the bars, "10 Aug … This week". */
@Composable
private fun ProgressCard(progress: ProgressUi) {
    val locale = currentLocale()
    val caption = stringResource(progress.metric.captionRes(), progress.weeks.size)
    val latest = progress.latest?.let { formatValue(progress.metric, it, locale) }
    val latestWithUnit = latest?.let { "$it ${stringResource(progress.metric.unitRes())}" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (latest != null) {
                Text(latest, style = MaterialTheme.typography.numberMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = " " + stringResource(progress.metric.unitRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SetwiseBarChart(
            values = progress.weeks,
            contentDescription = stringResource(
                R.string.a11y_progress_chart,
                caption,
                progress.weeks.count { it != null },
                progress.weeks.size,
                latestWithUnit ?: "–",
            ),
        )
        Row {
            Text(
                text = progress.firstWeek.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (latest == null) {
                    stringResource(R.string.progress_none_recently, progress.weeks.size)
                } else {
                    stringResource(R.string.this_week)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** "Fri, 2 Oct   40 × 6 · 40 × 6 · 37.5 × 8"; opens the workout. */
@Composable
private fun SessionRow(session: ExerciseSessionUi, kind: SetKind, onClick: () -> Unit) {
    val locale = currentLocale()
    val minutes = stringResource(R.string.unit_minutes)
    val km = stringResource(R.string.unit_km)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = session.date.toShortDayLabel(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = session.sets.mapNotNull { it.label(kind, locale, minutes, km) }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

/** Strength sets like the workout screen ("40 × 6"); cardio as "30 min · 5 km". */
private fun LoggedSet.label(kind: SetKind, locale: Locale, minutes: String, km: String): String? =
    if (kind == SetKind.Cardio) {
        listOfNotNull(
            durationSec?.let { "${(it / 60.0).roundToInt()} $minutes" },
            distanceKm?.takeIf { it > 0 }?.let { "${decimal(locale).format(it)} $km" },
        ).joinToString(" · ").ifEmpty { null }
    } else {
        PreviousSet(weightKg, reps, durationSec).label(kind)
    }

/** "48" kg (1RM, whole kg), "12" reps, "45" s, "12.4" km, "95" min. */
private fun formatValue(metric: ProgressMetric, value: Double, locale: Locale): String = when (metric) {
    ProgressMetric.Distance -> decimal(locale).format(value)
    else -> NumberFormat.getIntegerInstance(locale).format(value.roundToInt())
}

private fun decimal(locale: Locale) = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }

private fun ProgressMetric.captionRes(): Int = when (this) {
    ProgressMetric.EstimatedOneRepMax -> R.string.progress_e1rm
    ProgressMetric.Reps -> R.string.progress_reps
    ProgressMetric.Duration -> R.string.progress_duration
    ProgressMetric.Distance -> R.string.progress_distance
    ProgressMetric.Minutes -> R.string.progress_minutes
}

private fun ProgressMetric.unitRes(): Int = when (this) {
    ProgressMetric.EstimatedOneRepMax -> R.string.unit_kg
    ProgressMetric.Reps -> R.string.unit_reps
    ProgressMetric.Duration -> R.string.unit_seconds
    ProgressMetric.Distance -> R.string.unit_km
    ProgressMetric.Minutes -> R.string.unit_minutes
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun ExerciseDetailPreview() = SetwiseScreenPreview {
    ExerciseDetailScreen(
        uiState = ExerciseDetailUiState(
            isLoading = false,
            name = "Overhead Press (Barbell)",
            muscleGroup = "Shoulders",
            equipment = "Barbell",
            progress = ProgressUi(
                metric = ProgressMetric.EstimatedOneRepMax,
                weeks = listOf(40.0, 42.0, 44.5, null, 48.0, 48.0, 47.5, 48.0),
                firstWeek = LocalDate.of(2026, 8, 10),
                latest = 48.0,
            ),
            sessions = listOf(
                ExerciseSessionUi(1, LocalDate.of(2026, 10, 2), listOf(LoggedSet(40.0, 6, null, null), LoggedSet(37.5, 8, null, null))),
                ExerciseSessionUi(2, LocalDate.of(2026, 9, 29), listOf(LoggedSet(40.0, 6, null, null), LoggedSet(40.0, 5, null, null))),
            ),
        ),
        onBack = {},
        onSessionClick = {},
    )
}

@ScreenPreviews
@Composable
private fun ExerciseDetailEmptyPreview() = SetwiseScreenPreview {
    ExerciseDetailScreen(
        uiState = ExerciseDetailUiState(isLoading = false, name = "Face Pull", muscleGroup = "Shoulders", equipment = "Cable"),
        onBack = {},
        onSessionClick = {},
    )
}
