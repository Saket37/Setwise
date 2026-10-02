package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved routine, e.g. "Push Day". Its exercises are in [TemplateExerciseEntity]. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Short tag shown on the card, e.g. "Push", "Legs", "Cardio". Null if none. */
    val category: String? = null,
    /** Epoch millis. Used to order templates (newest last). */
    val createdAt: Long,
)
