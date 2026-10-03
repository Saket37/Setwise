package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.NextSession
import dev.saketanand.setwise.domain.model.ProgressionRule
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseHintBannerDefaults
import dev.saketanand.setwise.ui.workout.SetKind
import dev.saketanand.setwise.util.toWeightLabel
import kotlin.math.roundToInt

/** Text for a [NextSession], shared by the workout card's hint and Exercise detail. */

/** "62.5 kg × 8", "11 reps", "10 kg × 11", "50 s". */
@Composable
fun NextSession.target(): String {
    val locale = currentLocale()
    return when {
        seconds != null -> stringResource(R.string.progression_target_seconds, seconds)
        weightKg != null -> stringResource(R.string.progression_target_weight, weightKg.toWeightLabel(locale), reps ?: 0)
        else -> stringResource(R.string.progression_target_reps, reps ?: 0)
    }
}

/** "Try **62.5 kg × 8** today: you hit all reps at 60 kg twice"; null when it changes nothing. */
@Composable
fun NextSession.hint(): AnnotatedString? {
    val locale = currentLocale()
    val target = target()
    val text = when (rule) {
        ProgressionRule.AddWeight -> stringResource(R.string.progression_hint_add_weight, target, (done.weightKg ?: 0.0).toWeightLabel(locale))
        ProgressionRule.AddRep -> stringResource(R.string.progression_hint_add_rep, target, done.reps ?: 0)
        ProgressionRule.AddTime -> stringResource(R.string.progression_hint_add_time, target, done.seconds ?: 0)
        ProgressionRule.Lighter -> stringResource(R.string.progression_hint_lighter, target)
        ProgressionRule.Repeat -> return null
    }
    return SetwiseHintBannerDefaults.highlighted(text, target)
}

/** "Rule: every set reached 8 reps at 60 kg two sessions in a row, so add 2.5 kg." */
@Composable
fun NextSession.ruleText(kind: SetKind): String {
    val locale = currentLocale()
    return when (rule) {
        ProgressionRule.AddWeight -> stringResource(
            R.string.progression_rule_add_weight,
            done.reps ?: 0,
            (done.weightKg ?: 0.0).toWeightLabel(locale),
            (stepKg ?: 0.0).toWeightLabel(locale),
        )
        ProgressionRule.AddRep -> stringResource(R.string.progression_rule_add_rep, done.reps ?: 0)
        ProgressionRule.AddTime -> stringResource(R.string.progression_rule_add_time, done.seconds ?: 0, (seconds ?: 0) - (done.seconds ?: 0))
        ProgressionRule.Lighter -> stringResource(
            R.string.progression_rule_lighter,
            weightKg?.let { done.weightKg?.let { before -> ((1 - it / before) * 100).roundToInt() } } ?: 0,
            (reps ?: 0) - (done.reps ?: 0),
        )
        ProgressionRule.Repeat -> when (kind) {
            SetKind.Duration -> stringResource(R.string.progression_rule_repeat_time, done.seconds ?: 0)
            SetKind.Bodyweight -> stringResource(R.string.progression_rule_repeat_reps, done.reps ?: 0)
            else -> stringResource(R.string.progression_rule_repeat_weight, done.reps ?: 0)
        }
    }
}
