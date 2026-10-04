package dev.saketanand.setwise.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.BestSetFact
import dev.saketanand.setwise.domain.model.BodyPart
import dev.saketanand.setwise.domain.model.WeekFacts
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButtonDefaults
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import dev.saketanand.setwise.ui.designsystem.theme.pr
import dev.saketanand.setwise.util.toShortDurationLabel
import dev.saketanand.setwise.util.toWeightLabel
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toKotlinDuration

/**
 * Artboard 13: "Your week" (last week): workouts, time, PRs and volume; the recap (written
 * on-device, or the template from the same facts); highlights; "See <exercise> plan" for a
 * stalled lift. × closes it for the week.
 */
@Composable
fun WeeklySummaryCard(
    summary: WeeklySummaryUi,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val facts = summary.facts
    val locale = currentLocale()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.weekly_your_week),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "  " + facts.weekStart.format(DateTimeFormatter.ofPattern("d MMM", locale)) + " – " +
                    facts.weekEnd.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SetwiseIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.weekly_close),
                onClick = { onAction(HomeAction.OnWeeklySummaryDismiss) },
                size = 40.dp,
                iconSize = 18.dp,
                colors = SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Column(modifier = Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(facts.workouts.toString(), pluralStringResource(R.plurals.weekly_stat_workouts, facts.workouts), Modifier.weight(1f))
                Stat(facts.timeTrained.toKotlinDuration().toShortDurationLabel(), stringResource(R.string.weekly_stat_trained), Modifier.weight(1f))
                Stat(facts.prs.toString(), pluralStringResource(R.plurals.weekly_stat_prs, facts.prs), Modifier.weight(1f), MaterialTheme.colorScheme.pr)
                val change = facts.volumeChangePercent
                if (change != null) {
                    Stat(signed(change) + "%", stringResource(R.string.weekly_stat_volume), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                } else {
                    Stat(compact(facts.volumeKg, locale), stringResource(R.string.weekly_stat_volume_kg), Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_ai_sparkle), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.weekly_recap), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = summary.recap ?: if (summary.isGeneratingRecap) stringResource(R.string.weekly_writing) else templateRecap(facts),
                style = MaterialTheme.typography.bodyLarge,
                color = if (summary.recap == null && summary.isGeneratingRecap) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Highlights(facts, locale)
            facts.plateau?.let { plateau ->
                SetwiseButton(
                    text = stringResource(R.string.weekly_see_plan, plateau.exerciseName.lowercase(locale)),
                    onClick = { onAction(HomeAction.OnPlateauExerciseClick(plateau.exerciseId)) },
                    style = SetwiseButtonStyle.Outlined,
                    size = SetwiseButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.numberLarge, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** • Back Squat best set 100 × 5 · • Leg volume vs last week +18% · • Overhead Press estimated 1RM flat 4 wks. */
@Composable
private fun Highlights(facts: WeekFacts, locale: Locale) {
    val rows = buildList {
        facts.bestSet?.let { set ->
            add(Triple(stringResource(R.string.weekly_best_set, set.exerciseName), set.short(locale), if (set.isPr) MaterialTheme.colorScheme.pr else MaterialTheme.colorScheme.primary))
        }
        facts.bodyPartChange?.let { change ->
            val color = if (change.percent > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            add(Triple(stringResource(R.string.weekly_part_volume, stringResource(change.part.labelRes())), signed(change.percent) + "%", color))
        }
        facts.plateau?.let { plateau ->
            add(Triple(stringResource(R.string.weekly_plateau, plateau.exerciseName), pluralStringResource(R.plurals.weekly_flat_weeks, plateau.weeks, plateau.weeks), MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.numberSmall, color = color)
            }
        }
    }
}

/** The recap without the model, from the same facts. */
@Composable
private fun templateRecap(facts: WeekFacts): String {
    val locale = currentLocale()
    val workouts = pluralStringResource(R.plurals.weekly_template_workouts, facts.workouts, facts.workouts)
    return buildList {
        add(
            if (facts.prs > 0) {
                stringResource(R.string.weekly_template_week, workouts, pluralStringResource(R.plurals.weekly_template_prs, facts.prs, facts.prs))
            } else {
                stringResource(R.string.weekly_template_week_no_prs, workouts)
            },
        )
        facts.bestSet?.let { add(stringResource(R.string.weekly_template_best, it.exerciseName, it.short(locale))) }
        facts.volumeChangePercent?.let { add(stringResource(R.string.weekly_template_volume, signed(it))) }
        facts.plateau?.let { add(stringResource(R.string.weekly_template_plateau, it.exerciseName)) }
    }.joinToString(" ").replaceFirstChar { it.titlecase(locale) }
}

private fun BodyPart.labelRes() = when (this) {
    BodyPart.Legs -> R.string.weekly_part_legs
    BodyPart.Chest -> R.string.weekly_part_chest
    BodyPart.Back -> R.string.weekly_part_back
    BodyPart.Shoulders -> R.string.weekly_part_shoulders
    BodyPart.Arms -> R.string.weekly_part_arms
    BodyPart.Core -> R.string.weekly_part_core
}

private fun BestSetFact.short(locale: Locale) = when {
    weightKg != null && reps != null -> "${weightKg.toWeightLabel(locale)} × $reps"
    reps != null -> "$reps"
    else -> "${seconds ?: 0} s"
}

private fun signed(value: Int) = if (value > 0) "+$value" else "$value"

/** "38.2k". */
private fun compact(kg: Double, locale: Locale): String =
    if (kg >= 1000) NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(kg / 1000) + "k" else kg.toLong().toString()

@ComponentPreviews
@Composable
private fun WeeklySummaryCardPreview() = SetwisePreview {
    WeeklySummaryCard(
        summary = WeeklySummaryUi(
            facts = WeekFacts(
                weekStart = LocalDate.of(2026, 9, 28),
                workouts = 4,
                timeTrained = Duration.ofMinutes(274),
                prs = 3,
                volumeKg = 38_200.0,
                volumeChangePercent = 8,
                bestSet = BestSetFact("Back Squat", 100.0, 5, null, isPr = true),
                bodyPartChange = dev.saketanand.setwise.domain.model.BodyPartChange(BodyPart.Legs, 18),
                plateau = dev.saketanand.setwise.domain.model.PlateauFact(1, "Overhead Press", 4),
            ),
            recap = null,
            isGeneratingRecap = false,
        ),
        onAction = {},
    )
}
