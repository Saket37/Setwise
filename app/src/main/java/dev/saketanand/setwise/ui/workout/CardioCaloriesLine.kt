package dev.saketanand.setwise.ui.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.util.toWeightLabel
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * "About 310 kcal · ACSM treadmill formula at 78 kg" (#145): what this entry burns, by the same
 * formula as the workout's estimate, from the fields as typed. Nothing until it can be worked out.
 */
@Composable
internal fun CaloriesLine(basis: CardioCalorieBasis, uiState: CardioEntryUiState, fields: CardioFields) {
    val values = (parseCardio(fields.inputs(), uiState.metrics, uiState.inclinePct, uiState.level, uiState.lastTime) as? CardioParseResult.Valid)?.values
    val kcal = values?.let { CalorieFormula.cardioKcal(basis.method, basis.met, it, basis.kcalPerMetHour).roundToInt() }
    if (kcal == null || kcal <= 0) return
    val locale = currentLocale()
    val value = stringResource(R.string.cardio_kcal_value, NumberFormat.getIntegerInstance(locale).format(kcal))
    val formula = stringResource(
        when (basis.method) {
            CalorieMethod.ACSM_TREADMILL -> R.string.cardio_formula_treadmill
            CalorieMethod.ACSM_RUN_FROM_PACE -> R.string.cardio_formula_running
            CalorieMethod.ACSM_WALK_FROM_PACE -> R.string.cardio_formula_walking
            CalorieMethod.MET, null -> R.string.cardio_formula_met
        },
    )
    val how = if (basis.fromBmr) {
        stringResource(R.string.cardio_formula_from_bmr, formula)
    } else {
        stringResource(R.string.cardio_formula_at_weight, formula, basis.weightKg.toWeightLabel(locale))
    }
    val line = stringResource(R.string.cardio_kcal_line, value, how)
    val start = line.indexOf(value)
    Text(
        text = buildAnnotatedString {
            append(line)
            if (start >= 0) addStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold), start, start + value.length)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
