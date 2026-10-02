package dev.saketanand.setwise.data.seed

import kotlinx.serialization.Serializable

/**
 * Shape of assets/exercises.json. Property names must match the JSON keys exactly.
 * Enum-like values stay Strings here and are converted in the mapper, so domain enums
 * don't need serialization annotations.
 */
@Serializable
data class ExerciseSeedFile(
    /** Bump when built-in exercises are added, to re-seed on app update (future). */
    val version: Int,
    val exercises: List<ExerciseSeedDto>,
)

@Serializable
data class ExerciseSeedDto(
    val name: String,
    /** "STRENGTH" | "BODYWEIGHT" | "CARDIO" */
    val type: String,
    val muscleGroup: String,
    val equipment: String,
    val defaultRestSec: Int,
    // Only some entries have the fields below, so they need defaults.
    val timed: Boolean = false,
    /** Lowercase in the JSON, e.g. ["duration", "incline"]. */
    val metrics: List<String>? = null,
    val calorieMethod: String? = null,
    val met: Double? = null,
)
