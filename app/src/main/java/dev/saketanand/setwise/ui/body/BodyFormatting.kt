package dev.saketanand.setwise.ui.body

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.ProgressMeasure
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** The measure's name ("Body fat mass"). */
@StringRes
fun ProgressMeasure.nameRes(): Int = when (this) {
    ProgressMeasure.Weight -> R.string.measure_weight
    ProgressMeasure.BodyFat -> R.string.measure_body_fat
    ProgressMeasure.Muscle -> R.string.measure_muscle
    ProgressMeasure.Visceral -> R.string.measure_visceral
    ProgressMeasure.Bmr -> R.string.measure_bmr
    ProgressMeasure.FatMass -> R.string.measure_fat_mass
    ProgressMeasure.FatFreeMass -> R.string.measure_fat_free_mass
    ProgressMeasure.BodyWater -> R.string.measure_body_water
    ProgressMeasure.WaistHip -> R.string.measure_waist_hip
    ProgressMeasure.FitnessScore -> R.string.measure_fitness_score
    ProgressMeasure.Bmi -> R.string.measure_bmi
}

/** Decimal places each is shown with. */
private fun ProgressMeasure.decimals(): Int = when (this) {
    ProgressMeasure.Visceral, ProgressMeasure.Bmr, ProgressMeasure.FitnessScore -> 0
    ProgressMeasure.WaistHip -> 2
    else -> 1
}

/** The number alone, as [measureValue] shows it ("18.5"). */
fun measureNumber(measure: ProgressMeasure, value: Double, locale: Locale): String = number(value, measure.decimals(), locale)

private fun number(value: Double, decimals: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)

/** "78.4 kg", "18.5%", "Level 6", "1,750 kcal", "46.6 L", "0.88", "78". */
@Composable
@ReadOnlyComposable
fun measureValue(measure: ProgressMeasure, value: Double, locale: Locale): String {
    val n = number(value, measure.decimals(), locale)
    return when (measure) {
        ProgressMeasure.Weight, ProgressMeasure.Muscle, ProgressMeasure.FatMass, ProgressMeasure.FatFreeMass -> stringResource(R.string.value_kg, n)
        ProgressMeasure.BodyFat -> stringResource(R.string.value_percent, n)
        ProgressMeasure.Visceral -> stringResource(R.string.value_level, n)
        ProgressMeasure.Bmr -> stringResource(R.string.value_kcal, n)
        ProgressMeasure.BodyWater -> stringResource(R.string.value_litres, n)
        ProgressMeasure.WaistHip, ProgressMeasure.FitnessScore, ProgressMeasure.Bmi -> n
    }
}

/** A segment's lean mass as reports print it: "3.62 kg" for an arm or leg, "28.0 kg" for the trunk. */
@Composable
@ReadOnlyComposable
fun segmentLeanValue(value: Double, locale: Locale): String =
    stringResource(R.string.value_kg, number(value, if (value < SEGMENT_TWO_DECIMALS_BELOW) 2 else 1, locale))

private const val SEGMENT_TWO_DECIMALS_BELOW = 10.0

/** "−2.1 kg", "+0.5 kg", "−1.2 pts" (body fat changes in points, not %), "±0". */
@Composable
@ReadOnlyComposable
fun measureChange(measure: ProgressMeasure, change: Double, locale: Locale): String {
    val n = (if (change > 0) "+" else if (change < 0) "−" else "±") + number(abs(change), measure.decimals(), locale)
    return when (measure) {
        ProgressMeasure.Weight, ProgressMeasure.Muscle, ProgressMeasure.FatMass, ProgressMeasure.FatFreeMass -> stringResource(R.string.value_kg, n)
        ProgressMeasure.BodyFat -> stringResource(R.string.value_points, n)
        ProgressMeasure.Bmr -> stringResource(R.string.value_kcal, n)
        ProgressMeasure.BodyWater -> stringResource(R.string.value_litres, n)
        ProgressMeasure.Visceral, ProgressMeasure.WaistHip, ProgressMeasure.FitnessScore, ProgressMeasure.Bmi -> n
    }
}

/** "6 Jul". */
fun LocalDate.toDayMonthLabel(locale: Locale): String = format(DateTimeFormatter.ofPattern("d MMM", locale))
