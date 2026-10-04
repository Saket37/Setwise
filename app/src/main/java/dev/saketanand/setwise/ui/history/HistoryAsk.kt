package dev.saketanand.setwise.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.ai.HistoryReply
import dev.saketanand.setwise.domain.model.AnsweredSet
import dev.saketanand.setwise.domain.model.AnsweredWorkout
import dev.saketanand.setwise.domain.model.HistoryAnswer
import dev.saketanand.setwise.domain.model.HistoryQuestion
import dev.saketanand.setwise.domain.model.PeriodName
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import dev.saketanand.setwise.ui.designsystem.theme.pr
import dev.saketanand.setwise.util.toWeightLabel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge

/** Design "Ask your history": the reply (or that it's looking), then "You can also ask". */
@Composable
fun AskPanel(ask: AskUi, onAction: (HistoryAction) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "reply") {
            val reply = ask.reply
            when {
                ask.isLooking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ask_looking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                reply is HistoryReply.Answered -> AnswerCard(reply, onAction)
                reply is HistoryReply.UnknownExercise -> ReplyText(stringResource(R.string.ask_unknown_exercise, reply.words))
                reply == HistoryReply.NotUnderstood -> ReplyText(stringResource(R.string.ask_not_understood))
            }
        }
        item(key = "suggestions") { Suggestions(onAsk = { onAction(HistoryAction.OnAsk(it)) }) }
    }
}

/** "YOU CAN ALSO ASK" and a few questions it can answer. */
@Composable
fun Suggestions(onAsk: (String) -> Unit, modifier: Modifier = Modifier) {
    val questions = listOf(
        stringResource(R.string.ask_suggestion_best),
        stringResource(R.string.ask_suggestion_volume),
        stringResource(R.string.ask_suggestion_count),
        stringResource(R.string.ask_suggestion_prs),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.ask_you_can_also_ask))
        questions.forEach { question ->
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onAsk(question) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ReplyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The answer in words, the workouts it points to, and what was looked up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerCard(reply: HistoryReply.Answered, onAction: (HistoryAction) -> Unit) {
    val answer = reply.answer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(answerText(answer), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        val workouts = when (answer) {
            is HistoryAnswer.Lifted -> listOf(answer.workout)
            is HistoryAnswer.Count -> answer.workouts
            else -> emptyList()
        }
        workouts.forEach { workout -> AnsweredWorkoutRow(workout, onClick = { onAction(HistoryAction.OnWorkoutClick(workout.id)) }) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ask_looked_up), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = lookedUp(answer.question) + if (reply.byModel) " · " + stringResource(R.string.ask_picked_on_device) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** "MON 28 · Leg Day · Back Squat 100 × 5 · PR"; opens the workout. */
@Composable
private fun AnsweredWorkoutRow(workout: AnsweredWorkout, onClick: () -> Unit) {
    val locale = currentLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                workout.date.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(workout.date.dayOfMonth.toString(), style = MaterialTheme.typography.numberLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            workout.highlight?.let { set ->
                Text(
                    text = listOfNotNull("${set.exerciseName.substringBefore(" (")} ${set.short(locale)}", stringResource(R.string.ask_pr).takeIf { set.isPr }).joinToString(" · "),
                    style = MaterialTheme.typography.numberSmall,
                    color = if (set.isPr) MaterialTheme.colorScheme.pr else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** The answer in a sentence, its key part in bold. */
@Composable
private fun answerText(answer: HistoryAnswer): AnnotatedString {
    val locale = currentLocale()
    val question = answer.question
    return when (answer) {
        is HistoryAnswer.Lifted -> {
            val set = answer.set
            val day = answer.workout.date.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale))
            when (question) {
                is HistoryQuestion.LastLifted -> {
                    val name = question.exercise.shortName()
                    val sentence = if (question.atLeastKg != null) {
                        stringResource(R.string.ask_last_lifted_kg, stringResource(R.string.ask_weight_kg, question.atLeastKg.toWeightLabel(locale)), name, day, set.long(locale), set.setNumber)
                    } else {
                        stringResource(R.string.ask_last_lifted, name, day, set.long(locale), set.setNumber)
                    }
                    bold(if (answer.isBest) sentence + " " + stringResource(R.string.ask_also_best, name) else sentence, day)
                }
                is HistoryQuestion.BestSet -> {
                    val best = if (set.weightKg != null && set.reps != null) {
                        stringResource(R.string.ask_weight_kg, set.weightKg.toWeightLabel(locale)) + " × " + set.reps
                    } else {
                        set.short(locale)
                    }
                    bold(stringResource(R.string.ask_best_set, question.exercise.shortName(), periodSuffix(question.period.name), best, day), best)
                }
                else -> AnnotatedString("")
            }
        }
        is HistoryAnswer.Total -> {
            val volume = stringResource(R.string.ask_volume_kg, NumberFormat.getIntegerInstance(locale).format(answer.volumeKg.toLong()))
            val workouts = pluralStringResource(R.plurals.ask_workouts, answer.workouts, answer.workouts)
            val q = question as HistoryQuestion.Volume
            val text = if (q.muscles != null) {
                stringResource(R.string.ask_volume_muscles, q.muscles.asked, volume, periodSuffix(q.period.name), workouts)
            } else {
                stringResource(R.string.ask_volume, volume, periodSuffix(q.period.name), workouts)
            }
            bold(text, volume)
        }
        is HistoryAnswer.Count -> when (question) {
            is HistoryQuestion.PrWorkouts -> {
                val count = pluralStringResource(R.plurals.ask_workouts, answer.count, answer.count)
                bold(stringResource(R.string.ask_count_prs, count, periodSuffix(question.period.name)), count)
            }
            is HistoryQuestion.Sessions -> {
                val what = question.exercise?.shortName() ?: question.muscles?.asked
                if (what == null) {
                    val count = pluralStringResource(R.plurals.ask_workouts, answer.count, answer.count)
                    bold(stringResource(R.string.ask_count_any, count, periodSuffix(question.period.name)), count)
                } else {
                    val times = pluralStringResource(R.plurals.ask_times, answer.count, answer.count)
                    bold(stringResource(R.string.ask_count_of, what, times, periodSuffix(question.period.name)), times)
                }
            }
            else -> AnnotatedString("")
        }
        is HistoryAnswer.NoneFound -> AnnotatedString(stringResource(R.string.ask_none_found, periodSuffix(question.periodName())))
    }
}

/** "last time lifted · Back Squat · ≥ 100 kg". */
@Composable
private fun lookedUp(question: HistoryQuestion): String {
    val locale = currentLocale()
    val parts = when (question) {
        is HistoryQuestion.LastLifted -> listOfNotNull(
            stringResource(R.string.ask_lookup_last),
            question.exercise.shortName(),
            question.atLeastKg?.let { "≥ " + stringResource(R.string.ask_weight_kg, it.toWeightLabel(locale)) },
        )
        is HistoryQuestion.BestSet -> listOf(stringResource(R.string.ask_lookup_best), question.exercise.shortName(), periodLabel(question.period.name))
        is HistoryQuestion.Volume -> listOfNotNull(stringResource(R.string.ask_lookup_volume), question.muscles?.asked, periodLabel(question.period.name))
        is HistoryQuestion.Sessions -> listOfNotNull(
            stringResource(R.string.ask_lookup_count),
            question.exercise?.shortName() ?: question.muscles?.asked,
            periodLabel(question.period.name),
        )
        is HistoryQuestion.PrWorkouts -> listOf(stringResource(R.string.ask_lookup_prs), periodLabel(question.period.name))
    }
    return parts.joinToString(" · ")
}

private fun HistoryQuestion.periodName(): PeriodName = when (this) {
    is HistoryQuestion.BestSet -> period.name
    is HistoryQuestion.Volume -> period.name
    is HistoryQuestion.Sessions -> period.name
    is HistoryQuestion.PrWorkouts -> period.name
    is HistoryQuestion.LastLifted -> PeriodName.AllTime
}

/** " in September 2026", " this week", or nothing for all time. */
@Composable
private fun periodSuffix(name: PeriodName): String = if (name == PeriodName.AllTime) "" else " " + periodLabel(name)

@Composable
private fun periodLabel(name: PeriodName): String = when (name) {
    PeriodName.AllTime -> stringResource(R.string.ask_lookup_all_time)
    PeriodName.ThisWeek -> stringResource(R.string.ask_period_this_week)
    PeriodName.LastWeek -> stringResource(R.string.ask_period_last_week)
    PeriodName.ThisMonth -> stringResource(R.string.ask_period_this_month)
    PeriodName.LastMonth -> stringResource(R.string.ask_period_last_month)
    PeriodName.ThisYear -> stringResource(R.string.ask_period_this_year)
    is PeriodName.InMonth -> stringResource(R.string.ask_period_in, name.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale())))
}

/** "Back Squat (Barbell)" → "Back Squat": how it's said. */
private fun dev.saketanand.setwise.domain.model.Exercise.shortName() = name.substringBefore(" (")

/** "100 × 5", "12 reps", "45 s". */
private fun AnsweredSet.short(locale: Locale): String = when {
    weightKg != null && reps != null -> "${weightKg.toWeightLabel(locale)} × $reps"
    reps != null -> "$reps reps"
    seconds != null -> "$seconds s"
    else -> ""
}

/** "100 kg for 5 reps". */
@Composable
private fun AnsweredSet.long(locale: Locale): String = when {
    weightKg != null && reps != null -> stringResource(R.string.ask_for_reps, stringResource(R.string.ask_weight_kg, weightKg.toWeightLabel(locale)), reps)
    else -> short(locale)
}

private fun bold(text: String, part: String): AnnotatedString = buildAnnotatedString {
    val start = text.indexOf(part)
    if (part.isEmpty() || start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
    append(text.substring(start + part.length))
}
