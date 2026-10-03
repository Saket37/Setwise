package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    // History is listed by date, newest first.
    indices = [Index(value = ["startedAt"])],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Template this workout was started from; null for an empty workout. */
    val templateId: Long? = null,
    /** Epoch millis. Editable by the user. */
    val startedAt: Long,
    /** Epoch millis. Null while the workout is in progress. */
    val endedAt: Long? = null,
    val calories: Int? = null,
    /** How [calories] was produced, e.g. "formula", "heart_rate". */
    val caloriesSource: String? = null,
    /** "light", "moderate" or "vigorous" (from the LLM or the heuristic). */
    val intensity: String? = null,
    /** The on-device model's insight on the summary screen; null until written (or after a time edit). */
    val summary: String? = null,
)
