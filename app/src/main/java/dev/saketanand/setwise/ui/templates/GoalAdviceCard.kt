package dev.saketanand.setwise.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.GoalAdvice
import dev.saketanand.setwise.domain.model.kg

/** "✦ About your goal": the pace the goal asks for against a steady one, and how the plan helps. */
@Composable
internal fun AdviceCard(ui: GoalAdviceUi, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_ai_sparkle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(if (ui.modelText != null) R.string.goal_advice_title_by_model else R.string.goal_advice_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp).semantics { heading() },
            )
        }
        Text(
            text = ui.modelText ?: adviceText(ui.advice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The facts in words, when the model didn't word them. */
@Composable
private fun adviceText(advice: GoalAdvice): String {
    val target = advice.target
    val pace = when {
        advice.kgPerWeek == null -> pluralStringResource(
            R.plurals.goal_advice_no_time, advice.steadyWeeksMax,
            advice.steadyLowKg.kg(), advice.steadyHighKg.kg(), target.kg.kg(), advice.steadyWeeksMin, advice.steadyWeeksMax,
        )
        advice.isFast -> pluralStringResource(
            R.plurals.goal_advice_fast, advice.steadyWeeksMax,
            target.kg.kg(), target.timeLabel.orEmpty(), advice.kgPerWeek.kg(),
            advice.steadyLowKg.kg(), advice.steadyHighKg.kg(), advice.steadyWeeksMin, advice.steadyWeeksMax,
        )
        else -> stringResource(R.string.goal_advice_steady, target.kg.kg(), target.timeLabel.orEmpty(), advice.kgPerWeek.kg())
    }
    return pace + " " + stringResource(if (target.losing) R.string.goal_advice_losing else R.string.goal_advice_gaining)
}
