package dev.saketanand.setwise.domain.model

data class Exercise(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: String,
    val equipment: String,
    val defaultRestSec: Int,
    val isTimed: Boolean,
    val isCustom: Boolean,
    /** Cardio inputs to show (e.g. treadmill: duration, incline, speed, distance). Null for strength/bodyweight. */
    val metrics: List<CardioMetric>?,
    val calorieMethod: CalorieMethod?,
    /** MET value for [CalorieMethod.MET] cardio; null otherwise. */
    val met: Double?,
)
