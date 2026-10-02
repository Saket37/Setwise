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
)
