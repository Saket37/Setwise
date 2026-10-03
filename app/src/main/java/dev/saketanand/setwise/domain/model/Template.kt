package dev.saketanand.setwise.domain.model

import java.time.Instant

/** A saved routine, e.g. "Push Day". */
data class Template(
    val id: Long,
    val name: String,
    /** Short tag, e.g. "Push". Null if none. */
    val category: String?,
    val exercises: List<TemplateExercise>,
    /** Start of the last finished workout from this template; null if never used. */
    val lastUsedAt: Instant?,
)

data class TemplateExercise(
    val exerciseId: Long,
    val name: String,
    val targetSets: Int,
    val restSec: Int,
    /** E.g. "Chest", shown in the template editor. */
    val muscleGroup: String = "",
    /** Logged as time / distance on the cardio screen, not in sets: [targetSets] is always 1. */
    val isCardio: Boolean = false,
)

/**
 * A template as the editor saves it: [id] = 0 for a new one. Exercises in order; the same
 * exercise can't appear twice.
 */
data class TemplateDraft(
    val id: Long,
    val name: String,
    val category: String?,
    val exercises: List<TemplateDraftExercise>,
)

data class TemplateDraftExercise(val exerciseId: Long, val targetSets: Int)
